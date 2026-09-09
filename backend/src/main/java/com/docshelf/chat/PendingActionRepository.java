// Spring Data repository for pending_action
package com.docshelf.chat;

import com.docshelf.chat.entity.PendingAction;
import com.docshelf.chat.entity.PendingActionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    List<PendingAction> findBySessionIdAndStatus(UUID sessionId, PendingActionStatus status);

    List<PendingAction> findByStatusAndExpiresAtBefore(PendingActionStatus status, Instant before);
}
