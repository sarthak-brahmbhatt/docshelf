// Redactor output: text with typed placeholders, placeholder to original value map, and per-type counts
package com.docshelf.llm;

import java.util.Map;

public record Redaction(String text, Map<String, String> placeholders, Map<String, Integer> counts) {

    public Redaction {
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        counts = counts == null ? Map.of() : Map.copyOf(counts);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Replaces placeholders in {@code s} with their original values (for rehydrating deterministic fields). */
    public String rehydrate(String s) {
        if (s == null) {
            return null;
        }
        String out = s;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }
}
