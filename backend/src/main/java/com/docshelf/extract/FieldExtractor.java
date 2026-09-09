// Typed field extraction for one document type; B2/B3/B4 implement per type
package com.docshelf.extract;

public interface FieldExtractor<T> {

    DocType type();

    ExtractionResult<T> extract(ExtractedText text, DocumentContext ctx);
}
