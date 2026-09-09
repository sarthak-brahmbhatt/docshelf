// Immutable description of an audited action, built with AuditEvent.of(action, origin)...build()
package com.docshelf.audit;

import com.docshelf.audit.entity.AuditAction;
import com.docshelf.audit.entity.AuditOrigin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        AuditAction action,
        AuditOrigin origin,
        String actor,
        UUID documentId,
        String documentLabel,
        UUID memberId,
        UUID contactId,
        String contactLabel,
        String fieldName,
        String channel,
        UUID sessionId,
        UUID pendingActionId,
        Map<String, Object> details) {

    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
        actor = actor == null || actor.isBlank() ? "owner" : actor;
    }

    public static Builder of(AuditAction action, AuditOrigin origin) {
        return new Builder(action, origin);
    }

    public static final class Builder {
        private final AuditAction action;
        private final AuditOrigin origin;
        private String actor = "owner";
        private UUID documentId;
        private String documentLabel;
        private UUID memberId;
        private UUID contactId;
        private String contactLabel;
        private String fieldName;
        private String channel;
        private UUID sessionId;
        private UUID pendingActionId;
        private final Map<String, Object> details = new LinkedHashMap<>();

        private Builder(AuditAction action, AuditOrigin origin) {
            this.action = action;
            this.origin = origin;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder document(UUID documentId, String documentLabel) {
            this.documentId = documentId;
            this.documentLabel = documentLabel;
            return this;
        }

        public Builder documentId(UUID documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder member(UUID memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder contact(UUID contactId, String contactLabel) {
            this.contactId = contactId;
            this.contactLabel = contactLabel;
            return this;
        }

        public Builder field(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder session(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder pendingAction(UUID pendingActionId) {
            this.pendingActionId = pendingActionId;
            return this;
        }

        /** Adds a detail; values must be non-sensitive (ids, counts, masked values, timings). */
        public Builder detail(String key, Object value) {
            if (value != null) {
                details.put(key, value);
            }
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(action, origin, actor, documentId, documentLabel, memberId, contactId,
                    contactLabel, fieldName, channel, sessionId, pendingActionId, details);
        }
    }
}
