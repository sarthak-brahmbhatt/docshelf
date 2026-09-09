// JDBC access to document_chunk for vector inserts and hybrid (cosine + ts_rank, 0.6/0.4 after min-max) search
package com.docshelf.document;

import com.docshelf.extract.DocType;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkSearchRepository {

    public static final int DIMENSIONS = 1536;
    public static final double VECTOR_WEIGHT = 0.6;
    public static final double KEYWORD_WEIGHT = 0.4;
    private static final int CANDIDATE_MULTIPLIER = 5;

    private static final String SEARCH_SQL = """
            WITH params AS (
                SELECT CAST(? AS vector) AS emb, plainto_tsquery('english', ?) AS tsq
            ),
            base AS (
                SELECT c.id, c.document_id, d.title, d.doc_type, d.member_id, c.chunk_index, c.page_no, c.section, c.text,
                       1 - (c.embedding <=> p.emb) AS vscore,
                       ts_rank_cd(c.tsv, p.tsq) AS kscore
                FROM document_chunk c
                JOIN document d ON d.id = c.document_id
                CROSS JOIN params p
                WHERE c.embedding IS NOT NULL
                  AND (CAST(? AS uuid) IS NULL OR d.member_id = CAST(? AS uuid))
                  AND (CAST(? AS text) IS NULL OR d.doc_type = CAST(? AS text))
            ),
            cand AS (
                (SELECT * FROM base ORDER BY vscore DESC LIMIT ?)
                UNION
                (SELECT * FROM base WHERE kscore > 0 ORDER BY kscore DESC LIMIT ?)
            ),
            norm AS (
                SELECT *,
                    CASE WHEN MAX(vscore) OVER () = MIN(vscore) OVER () THEN 1.0
                         ELSE (vscore - MIN(vscore) OVER ()) / (MAX(vscore) OVER () - MIN(vscore) OVER ()) END AS vn,
                    CASE WHEN MAX(kscore) OVER () = MIN(kscore) OVER () THEN (CASE WHEN kscore > 0 THEN 1.0 ELSE 0.0 END)
                         ELSE (kscore - MIN(kscore) OVER ()) / (MAX(kscore) OVER () - MIN(kscore) OVER ()) END AS kn
                FROM cand
            )
            SELECT id, document_id, title, doc_type, member_id, chunk_index, page_no, section, text, vscore, kscore,
                   (? * vn + ? * kn) AS score
            FROM norm
            ORDER BY score DESC, id
            LIMIT ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO document_chunk (document_id, chunk_index, page_no, section, text, embedding)
            VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
            ON CONFLICT (document_id, chunk_index) DO UPDATE
              SET page_no = EXCLUDED.page_no, section = EXCLUDED.section, text = EXCLUDED.text,
                  embedding = EXCLUDED.embedding
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public ChunkSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts or replaces one chunk (redacted text + embedding); returns the chunk id. */
    public long insertChunk(UUID documentId, int chunkIndex, Integer pageNo, String section, String text,
                            float[] embedding) {
        if (embedding != null && embedding.length != DIMENSIONS) {
            throw new IllegalArgumentException("Embedding must have " + DIMENSIONS + " dimensions, got " + embedding.length);
        }
        return jdbc.query(con -> {
            var ps = con.prepareStatement(INSERT_SQL);
            ps.setObject(1, documentId);
            ps.setInt(2, chunkIndex);
            if (pageNo == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, pageNo);
            }
            ps.setString(4, section);
            ps.setString(5, text);
            ps.setString(6, toVectorLiteral(embedding));
            return ps;
        }, rs -> {
            rs.next();
            return rs.getLong(1);
        });
    }

    public int deleteByDocument(UUID documentId) {
        return jdbc.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    }

    /**
     * Hybrid search: candidates from top-N cosine and top-N full-text, min-max normalised per signal,
     * combined 0.6 vector + 0.4 keyword, top {@code limit}.
     */
    public List<ChunkHit> search(float[] queryEmbedding, String queryText, UUID memberId, DocType docType, int limit) {
        int lim = Math.max(1, limit);
        int candidates = lim * CANDIDATE_MULTIPLIER;
        String emb = toVectorLiteral(queryEmbedding);
        String q = queryText == null ? "" : queryText;
        String type = docType == null ? null : docType.name();
        return jdbc.query(con -> {
            var ps = con.prepareStatement(SEARCH_SQL);
            int i = 1;
            ps.setString(i++, emb);
            ps.setString(i++, q);
            setUuid(ps, i++, memberId);
            setUuid(ps, i++, memberId);
            ps.setString(i++, type);
            ps.setString(i++, type);
            ps.setInt(i++, candidates);
            ps.setInt(i++, candidates);
            ps.setDouble(i++, VECTOR_WEIGHT);
            ps.setDouble(i++, KEYWORD_WEIGHT);
            ps.setInt(i, lim);
            return ps;
        }, (rs, n) -> new ChunkHit(
                rs.getLong("id"),
                rs.getObject("document_id", UUID.class),
                rs.getString("title"),
                DocType.valueOf(rs.getString("doc_type")),
                rs.getObject("member_id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getObject("page_no") == null ? null : rs.getInt("page_no"),
                rs.getString("section"),
                rs.getString("text"),
                rs.getDouble("vscore"),
                rs.getDouble("kscore"),
                rs.getDouble("score")));
    }

    private static void setUuid(java.sql.PreparedStatement ps, int index, UUID value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    /** pgvector text literal: {@code [0.1,0.2,...]}. */
    public static String toVectorLiteral(float[] v) {
        if (v == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(v.length * 10 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
