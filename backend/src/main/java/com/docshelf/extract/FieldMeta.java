// Per-field provenance: which strategy produced the value, how confident, on which page
package com.docshelf.extract;

public record FieldMeta(Source source, double confidence, Integer page) {

    public enum Source { RULE, LLM, OCR, VISION, USER }

    public static FieldMeta rule(double confidence, Integer page) {
        return new FieldMeta(Source.RULE, confidence, page);
    }

    public static FieldMeta llm(double confidence, Integer page) {
        return new FieldMeta(Source.LLM, confidence, page);
    }

    public static FieldMeta user() {
        return new FieldMeta(Source.USER, 1.0, null);
    }
}
