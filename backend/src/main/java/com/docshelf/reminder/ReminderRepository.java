// Spring Data repository for reminder (dispatch claiming uses FOR UPDATE SKIP LOCKED)
package com.docshelf.reminder;

import com.docshelf.reminder.entity.Reminder;
import com.docshelf.reminder.entity.ReminderSourceType;
import com.docshelf.reminder.entity.ReminderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Optional<Reminder> findByDedupeKey(String dedupeKey);

    List<Reminder> findByStatusInAndFireAtBetweenOrderByFireAtAsc(Collection<ReminderStatus> statuses, Instant from, Instant to);

    List<Reminder> findByDocumentIdOrderByFireAtAsc(UUID documentId);

    List<Reminder> findBySourceTypeAndSourceIdAndStatusIn(ReminderSourceType sourceType, UUID sourceId, Collection<ReminderStatus> statuses);

    List<Reminder> findByRuleKeyAndStatusIn(String ruleKey, Collection<ReminderStatus> statuses);

    @Query(value = "SELECT * FROM reminder WHERE status IN ('PENDING','SNOOZED') AND fire_at <= :now ORDER BY fire_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Reminder> claimable(@Param("now") Instant now, @Param("limit") int limit);
}
