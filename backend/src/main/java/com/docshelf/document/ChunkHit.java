// One hybrid-search result row: chunk identity, redacted snippet text and the combined score
package com.docshelf.document;

import com.docshelf.extract.DocType;
import java.util.UUID;

public record ChunkHit(
        long chunkId,
        UUID documentId,
        String documentTitle,
        DocType docType,
        UUID memberId,
        int chunkIndex,
        Integer pageNo,
        String section,
        String text,
        double vectorScore,
        double keywordScore,
        double score) {
}
