// Spring Data repository for outbound_message
package com.docshelf.messaging;

import com.docshelf.messaging.entity.OutboundMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {

    Optional<OutboundMessage> findByDedupeKey(String dedupeKey);

    List<OutboundMessage> findByAttachmentDocumentIdIsNotNullOrderByCreatedAtDesc();

    List<OutboundMessage> findByContactIdOrderByCreatedAtDesc(UUID contactId);
}
