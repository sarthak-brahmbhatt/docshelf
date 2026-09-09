// Inputs that influence classification: user type hint, filename, MIME, source, OCR provenance
package com.docshelf.extract;

public record ClassificationHints(DocType typeHint, String originalFilename, String mimeType, String source,
                                  boolean ocrUsed, Double meanOcrConfidence) {

    public static ClassificationHints none() {
        return new ClassificationHints(null, null, null, null, false, null);
    }
}
