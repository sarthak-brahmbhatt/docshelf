// Output of a FieldExtractor: typed fields, per-field provenance, overall confidence, review reasons, date events
package com.docshelf.extract;

import java.util.List;
import java.util.Map;

public record ExtractionResult<T>(T fields, Map<String, FieldMeta> meta, double confidence,
                                  List<String> reviewReasons, List<DocumentEventDraft> events) {

    public ExtractionResult {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
        events = events == null ? List.of() : List.copyOf(events);
    }

    public boolean needsReview() {
        return !reviewReasons.isEmpty();
    }
}
