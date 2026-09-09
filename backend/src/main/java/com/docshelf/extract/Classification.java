// Classifier output: type, confidence 0..1 and which strategy produced it
package com.docshelf.extract;

public record Classification(DocType type, double confidence, Source source, String reason) {

    public enum Source { RULE, LLM, USER }

    public static Classification unknown() {
        return new Classification(DocType.UNKNOWN, 0.0, Source.RULE, "no signal");
    }
}
