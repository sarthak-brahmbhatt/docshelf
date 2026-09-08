// Small helpers for UUID identifiers used across controllers and services
package com.docshelf.common;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }

    /** Parses a UUID or throws {@link BadRequestException}; null/blank -> null. */
    public static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid id: " + value);
        }
    }

    public static UUID parse(String value) {
        UUID id = parseOrNull(value);
        if (id == null) {
            throw new BadRequestException("Missing id");
        }
        return id;
    }
}
