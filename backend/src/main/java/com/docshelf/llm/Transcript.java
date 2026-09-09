// Speech-to-text result
package com.docshelf.llm;

public record Transcript(String text, String language, Double durationSeconds) {
}
