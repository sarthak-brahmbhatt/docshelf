// Spring Data repository for document_event
package com.docshelf.document;

import com.docshelf.document.entity.DocumentEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentEventRepository extends JpaRepository<DocumentEvent, UUID> {

    List<DocumentEvent> findByDocumentIdOrderByEventAtAsc(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    List<DocumentEvent> findByAutoReminderTrueAndEventAtAfter(Instant after);
}
