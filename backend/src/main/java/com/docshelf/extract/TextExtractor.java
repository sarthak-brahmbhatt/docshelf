// One implementation per MIME family (PDF, image, text, docx, audio); B1 implements
package com.docshelf.extract;

public interface TextExtractor {

    boolean supports(String mimeType);

    ExtractedText extract(byte[] bytes, String mimeType, ExtractionHints hints);
}
