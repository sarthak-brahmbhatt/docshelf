// Inputs that influence text extraction: PDF password candidates, OCR languages, original filename, type hint
package com.docshelf.extract;

import java.util.List;

public record ExtractionHints(
        String originalFilename,
        List<String> passwordCandidates,
        List<String> ocrLanguages,
        DocType typeHint,
        boolean forceOcr) {

    public ExtractionHints {
        passwordCandidates = passwordCandidates == null ? List.of() : List.copyOf(passwordCandidates);
        ocrLanguages = ocrLanguages == null ? List.of("eng") : List.copyOf(ocrLanguages);
    }

    public static ExtractionHints none() {
        return new ExtractionHints(null, List.of(), List.of("eng"), null, false);
    }

    public static ExtractionHints of(String originalFilename, DocType typeHint) {
        return new ExtractionHints(originalFilename, List.of(), List.of("eng"), typeHint, false);
    }
}
