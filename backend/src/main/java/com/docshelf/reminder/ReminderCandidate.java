// A reminder-worthy event discovered by a ReminderSourceProvider; the materializer turns it into reminder rows
package com.docshelf.reminder;

import com.docshelf.reminder.entity.ReminderSourceType;
import java.time.Instant;
import java.util.UUID;

public record ReminderCandidate(
        ReminderSourceType sourceType,
        UUID sourceId,
        UUID memberId,
        UUID documentId,
        Instant eventAt,
        String title,
        String body) {
}
