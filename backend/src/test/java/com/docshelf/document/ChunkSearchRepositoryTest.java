// ChunkSearchRepository: vector insert/upsert, hybrid search ranking, member and type filters, JPA read-back of the vector
package com.docshelf.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.docshelf.AbstractIntegrationTest;
import com.docshelf.document.entity.DocSource;
import com.docshelf.document.entity.Document;
import com.docshelf.document.entity.DocumentChunk;
import com.docshelf.extract.DocStatus;
import com.docshelf.extract.DocType;
import com.docshelf.member.FamilyMemberRepository;
import com.docshelf.member.entity.FamilyMember;
import com.docshelf.member.entity.Relation;
import com.docshelf.testsupport.FakeEmbeddingClient;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChunkSearchRepositoryTest extends AbstractIntegrationTest {

    @Autowired ChunkSearchRepository chunks;
    @Autowired DocumentRepository documents;
    @Autowired DocumentChunkRepository chunkJpa;
    @Autowired FamilyMemberRepository members;

    private Document newDocument(String title, DocType type, UUID memberId) {
        Document d = new Document();
        d.setTitle(title);
        d.setDocType(type);
        d.setSource(DocSource.UPLOAD);
        d.setStatus(DocStatus.READY);
        d.setMemberId(memberId);
        d.setOriginalFilename(title + ".pdf");
        d.setMimeType("application/pdf");
        d.setSizeBytes(10);
        d.setSha256(DigestUtils.sha256Hex(title + UUID.randomUUID()));
        d.setStoragePath("2026/09/" + UUID.randomUUID() + ".bin");
        d.setDekWrapped(new byte[40]);
        d.setKekId("env-v1");
        return documents.save(d);
    }

    @Test
    void insertAndHybridSearchRoundTrip() {
        FamilyMember mother = new FamilyMember();
        mother.setFullName("Sunita");
        mother.setRelation(Relation.MOTHER);
        mother = members.save(mother);

        Document policy = newDocument("Star Health policy", DocType.INSURANCE_POLICY, mother.getId());
        Document cas = newDocument("CAS statement", DocType.MF_CAS, null);

        String policyText = "Star Health Family Health Optima policy number P/123456/01 sum insured Rs 10,00,000 "
                + "premium due 14 March 2027 nominee spouse";
        String casText = "Consolidated Account Statement folio 1234567/89 Axis Bluechip Fund units 123.456 "
                + "closing balance NAV 45.6789";
        long id1 = chunks.insertChunk(policy.getId(), 0, 1, "policy", policyText, FakeEmbeddingClient.embedOne(policyText));
        long id2 = chunks.insertChunk(cas.getId(), 0, 1, "folio", casText, FakeEmbeddingClient.embedOne(casText));
        assertThat(id1).isNotEqualTo(id2);

        // upsert on (document_id, chunk_index) keeps the id
        long again = chunks.insertChunk(policy.getId(), 0, 1, "policy", policyText, FakeEmbeddingClient.embedOne(policyText));
        assertThat(again).isEqualTo(id1);

        String q = "when is the premium due on the Star Health policy";
        List<ChunkHit> hits = chunks.search(FakeEmbeddingClient.embedOne(q), q, null, null, 8);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).documentId()).isEqualTo(policy.getId());
        assertThat(hits.get(0).documentTitle()).isEqualTo("Star Health policy");
        assertThat(hits.get(0).docType()).isEqualTo(DocType.INSURANCE_POLICY);
        assertThat(hits.get(0).memberId()).isEqualTo(mother.getId());
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
        assertThat(hits.get(0).keywordScore()).isGreaterThan(0);
        assertThat(hits.get(0).text()).contains("premium due");

        // exact-token keyword signal: folio number only appears in the CAS chunk
        String q2 = "folio 1234567/89 closing balance";
        List<ChunkHit> casHits = chunks.search(FakeEmbeddingClient.embedOne(q2), q2, null, null, 8);
        assertThat(casHits.get(0).documentId()).isEqualTo(cas.getId());

        // filters
        assertThat(chunks.search(FakeEmbeddingClient.embedOne(q), q, mother.getId(), null, 8))
                .extracting(ChunkHit::documentId).containsExactly(policy.getId());
        assertThat(chunks.search(FakeEmbeddingClient.embedOne(q), q, null, DocType.MF_CAS, 8))
                .extracting(ChunkHit::documentId).containsExactly(cas.getId());
        assertThat(chunks.search(FakeEmbeddingClient.embedOne(q), q, UUID.randomUUID(), null, 8)).isEmpty();

        // JPA mapping of the vector column via hibernate-vector
        List<DocumentChunk> viaJpa = chunkJpa.findByDocumentIdOrderByChunkIndexAsc(policy.getId());
        assertThat(viaJpa).hasSize(1);
        assertThat(viaJpa.get(0).getEmbedding()).hasSize(1536);
        DocumentChunk jpaChunk = new DocumentChunk();
        jpaChunk.setDocumentId(cas.getId());
        jpaChunk.setChunkIndex(1);
        jpaChunk.setText("summary chunk");
        jpaChunk.setEmbedding(FakeEmbeddingClient.embedOne("summary chunk"));
        chunkJpa.saveAndFlush(jpaChunk);
        assertThat(chunkJpa.countByDocumentId(cas.getId())).isEqualTo(2);

        assertThat(chunks.deleteByDocument(cas.getId())).isEqualTo(2);
        documents.deleteAll(List.of(policy, cas));
        members.delete(mother);
    }
}
