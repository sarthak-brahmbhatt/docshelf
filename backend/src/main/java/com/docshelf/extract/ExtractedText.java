// Page-wise extracted text with OCR provenance; fullText() joins pages with form feeds
package com.docshelf.extract;

import java.util.List;
import java.util.stream.Collectors;

public record ExtractedText(List<Page> pages, boolean ocrUsed, Double meanOcrConfidence, int charCount) {

    public record Page(int pageNo, String text) {
    }

    public ExtractedText {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public static ExtractedText of(List<Page> pages, boolean ocrUsed, Double meanOcrConfidence) {
        int chars = pages == null ? 0 : pages.stream().mapToInt(p -> p.text() == null ? 0 : p.text().length()).sum();
        return new ExtractedText(pages, ocrUsed, meanOcrConfidence, chars);
    }

    public static ExtractedText single(String text) {
        return of(List.of(new Page(1, text == null ? "" : text)), false, null);
    }

    public static ExtractedText empty() {
        return of(List.of(), false, null);
    }

    public String fullText() {
        return pages.stream().map(p -> p.text() == null ? "" : p.text()).collect(Collectors.joining("\f"));
    }

    /** Text of the first {@code n} pages joined with form feeds (used for classification prompts). */
    public String firstPages(int n) {
        return pages.stream().limit(n).map(p -> p.text() == null ? "" : p.text()).collect(Collectors.joining("\f"));
    }

    public int pageCount() {
        return pages.size();
    }

    public boolean isEmpty() {
        return charCount == 0;
    }
}
