// Accounting row for every OpenAI call: purpose, model, tokens, redaction counts, latency, status
package com.docshelf.llm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "llm_call")
public class LlmCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "model")
    private String model;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redaction_counts")
    private Map<String, Integer> redactionCounts;

    @Column(name = "image_sent")
    private boolean imageSent;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "status")
    private String status;

    @Column(name = "error")
    private String error;

    @Column(name = "openai_response_id")
    private String openaiResponseId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Map<String, Integer> getRedactionCounts() {
        return redactionCounts;
    }

    public void setRedactionCounts(Map<String, Integer> redactionCounts) {
        this.redactionCounts = redactionCounts;
    }

    public boolean isImageSent() {
        return imageSent;
    }

    public void setImageSent(boolean imageSent) {
        this.imageSent = imageSent;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getOpenaiResponseId() {
        return openaiResponseId;
    }

    public void setOpenaiResponseId(String openaiResponseId) {
        this.openaiResponseId = openaiResponseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
