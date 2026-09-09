// Core document row: identity, encrypted blob pointers, ingestion state and provenance
package com.docshelf.document.entity;

import com.docshelf.extract.DocStatus;
import com.docshelf.extract.DocType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "member_id")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocType docType = DocType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private DocSource source = DocSource.UPLOAD;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DocStatus status;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "unlocked_storage_path")
    private String unlockedStoragePath;

    @Column(name = "dek_wrapped")
    private byte[] dekWrapped;

    @Column(name = "kek_id")
    private String kekId;

    @Column(name = "pdf_password_encrypted")
    private byte[] pdfPasswordEncrypted;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "ocr_used")
    private boolean ocrUsed;

    @Column(name = "vision_used")
    private boolean visionUsed;

    @Column(name = "text_chars")
    private Integer textChars;

    @Enumerated(EnumType.STRING)
    @Column(name = "classifier_source")
    private ClassifierSource classifierSource;

    @Column(name = "classifier_confidence", precision = 4, scale = 3)
    private BigDecimal classifierConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_source")
    private ExtractionSource extractionSource;

    @Column(name = "extraction_confidence", precision = 4, scale = 3)
    private BigDecimal extractionConfidence;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "needs_review_reasons")
    private List<String> needsReviewReasons;

    @Column(name = "last_error")
    private String lastError;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public void setMemberId(UUID memberId) {
        this.memberId = memberId;
    }

    public DocType getDocType() {
        return docType;
    }

    public void setDocType(DocType docType) {
        this.docType = docType;
    }

    public DocSource getSource() {
        return source;
    }

    public void setSource(DocSource source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DocStatus getStatus() {
        return status;
    }

    public void setStatus(DocStatus status) {
        this.status = status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getUnlockedStoragePath() {
        return unlockedStoragePath;
    }

    public void setUnlockedStoragePath(String unlockedStoragePath) {
        this.unlockedStoragePath = unlockedStoragePath;
    }

    public byte[] getDekWrapped() {
        return dekWrapped;
    }

    public void setDekWrapped(byte[] dekWrapped) {
        this.dekWrapped = dekWrapped;
    }

    public String getKekId() {
        return kekId;
    }

    public void setKekId(String kekId) {
        this.kekId = kekId;
    }

    public byte[] getPdfPasswordEncrypted() {
        return pdfPasswordEncrypted;
    }

    public void setPdfPasswordEncrypted(byte[] pdfPasswordEncrypted) {
        this.pdfPasswordEncrypted = pdfPasswordEncrypted;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public boolean isOcrUsed() {
        return ocrUsed;
    }

    public void setOcrUsed(boolean ocrUsed) {
        this.ocrUsed = ocrUsed;
    }

    public boolean isVisionUsed() {
        return visionUsed;
    }

    public void setVisionUsed(boolean visionUsed) {
        this.visionUsed = visionUsed;
    }

    public Integer getTextChars() {
        return textChars;
    }

    public void setTextChars(Integer textChars) {
        this.textChars = textChars;
    }

    public ClassifierSource getClassifierSource() {
        return classifierSource;
    }

    public void setClassifierSource(ClassifierSource classifierSource) {
        this.classifierSource = classifierSource;
    }

    public BigDecimal getClassifierConfidence() {
        return classifierConfidence;
    }

    public void setClassifierConfidence(BigDecimal classifierConfidence) {
        this.classifierConfidence = classifierConfidence;
    }

    public ExtractionSource getExtractionSource() {
        return extractionSource;
    }

    public void setExtractionSource(ExtractionSource extractionSource) {
        this.extractionSource = extractionSource;
    }

    public BigDecimal getExtractionConfidence() {
        return extractionConfidence;
    }

    public void setExtractionConfidence(BigDecimal extractionConfidence) {
        this.extractionConfidence = extractionConfidence;
    }

    public List<String> getNeedsReviewReasons() {
        return needsReviewReasons;
    }

    public void setNeedsReviewReasons(List<String> needsReviewReasons) {
        this.needsReviewReasons = needsReviewReasons;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
