// Ingestion status machine (matches document.status CHECK constraint)
package com.docshelf.extract;

import java.util.EnumSet;
import java.util.Set;

public enum DocStatus {
    UPLOADED, NEEDS_PASSWORD, EXTRACTING_TEXT, CLASSIFYING, EXTRACTING_FIELDS, INDEXING, READY, NEEDS_REVIEW, FAILED;

    private static final Set<DocStatus> TERMINAL = EnumSet.of(READY, NEEDS_REVIEW, FAILED, NEEDS_PASSWORD);

    /** True when the UI can stop polling (no worker step is pending). */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
