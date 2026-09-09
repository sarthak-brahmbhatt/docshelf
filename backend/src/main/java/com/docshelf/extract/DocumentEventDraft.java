// A date found in a document (appointment, expiry, refill...) before it is persisted as document_event
package com.docshelf.extract;

import java.time.Instant;
import java.util.List;

public record DocumentEventDraft(Kind kind, String label, Instant eventAt, boolean allDay, boolean autoReminder,
                                 List<Integer> leadDays, Double confidence) {

    public enum Kind { APPOINTMENT, FOLLOW_UP, EXPIRY, DUE, REFILL, RENEWAL, OTHER }

    public DocumentEventDraft {
        leadDays = leadDays == null || leadDays.isEmpty() ? List.of(1, 0) : List.copyOf(leadDays);
    }
}
