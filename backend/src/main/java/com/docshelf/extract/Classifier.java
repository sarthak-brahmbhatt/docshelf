// Determines the document type from extracted text (rule-based first, LLM fallback); B2 implements
package com.docshelf.extract;

public interface Classifier {

    Classification classify(ExtractedText text, ClassificationHints hints);
}
