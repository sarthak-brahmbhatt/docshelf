// One provider per reminder_rule.rule_key (policy expiry, premium due, birthday, follow-up, refill, document event); B5 implements
package com.docshelf.reminder;

import java.time.LocalDate;
import java.util.List;

public interface ReminderSourceProvider {

    /** The reminder_rule.rule_key this provider feeds. */
    String ruleKey();

    /** Upcoming events as of {@code today}; one candidate per source with its next event date. */
    List<ReminderCandidate> candidates(LocalDate today);
}
