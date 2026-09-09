// A materialised reminder occurrence (one per rule x source x event x offset x channel), idempotent by dedupe_key
package com.docshelf.reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "reminder")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "rule_key")
    private String ruleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin")
    private ReminderOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private ReminderSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "event_at")
    private Instant eventAt;

    @Column(name = "offset_days")
    private int offsetDays;

    @Column(name = "fire_at")
    private Instant fireAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private ReminderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "notes")
    private String notes;

    @Column(name = "dedupe_key")
    private String dedupeKey;

    @Column(name = "attempts")
    private int attempts;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "outbound_message_id")
    private UUID outboundMessageId;

    @Column(name = "error")
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public ReminderOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(ReminderOrigin origin) {
        this.origin = origin;
    }

    public ReminderSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ReminderSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public void setMemberId(UUID memberId) {
        this.memberId = memberId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public void setEventAt(Instant eventAt) {
        this.eventAt = eventAt;
    }

    public int getOffsetDays() {
        return offsetDays;
    }

    public void setOffsetDays(int offsetDays) {
        this.offsetDays = offsetDays;
    }

    public Instant getFireAt() {
        return fireAt;
    }

    public void setFireAt(Instant fireAt) {
        this.fireAt = fireAt;
    }

    public ReminderChannel getChannel() {
        return channel;
    }

    public void setChannel(ReminderChannel channel) {
        this.channel = channel;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ReminderStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public UUID getOutboundMessageId() {
        return outboundMessageId;
    }

    public void setOutboundMessageId(UUID outboundMessageId) {
        this.outboundMessageId = outboundMessageId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
