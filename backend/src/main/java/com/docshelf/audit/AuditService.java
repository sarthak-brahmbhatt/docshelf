// Writes audit_log rows in their own transaction so an audit entry survives a rolled-back business transaction
package com.docshelf.audit;

import com.docshelf.audit.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Always inserts (REQUIRES_NEW); returns the persisted row (its id is the auditId shown to the UI). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuditEvent event) {
        AuditLog row = new AuditLog();
        row.setActor(event.actor());
        row.setOrigin(event.origin());
        row.setAction(event.action());
        row.setDocumentId(event.documentId());
        row.setDocumentLabel(event.documentLabel());
        row.setMemberId(event.memberId());
        row.setContactId(event.contactId());
        row.setContactLabel(event.contactLabel());
        row.setFieldName(event.fieldName());
        row.setChannel(event.channel());
        row.setSessionId(event.sessionId());
        row.setPendingActionId(event.pendingActionId());
        row.setDetails(new java.util.LinkedHashMap<>(event.details()));
        AuditLog saved = repository.saveAndFlush(row);
        log.debug("audit {} {} document={} id={}", event.origin(), event.action(), event.documentId(), saved.getId());
        return saved;
    }

    /** Convenience overload. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuditEvent.Builder builder) {
        return record(builder.build());
    }
}
