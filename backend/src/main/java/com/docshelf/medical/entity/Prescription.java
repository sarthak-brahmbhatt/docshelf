// Typed fields for PRESCRIPTION documents (doctor, visit, follow-up)
package com.docshelf.medical.entity;

import com.docshelf.extract.FieldMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "hospital")
    private String hospital;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "diagnosis")
    private String diagnosis;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_note")
    private String followUpNote;

    @Column(name = "notes")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_meta")
    private Map<String, FieldMeta> fieldMeta = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_extraction")
    private Map<String, Object> rawExtraction;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public void setMemberId(UUID memberId) {
        this.memberId = memberId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getHospital() {
        return hospital;
    }

    public void setHospital(String hospital) {
        this.hospital = hospital;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getFollowUpNote() {
        return followUpNote;
    }

    public void setFollowUpNote(String followUpNote) {
        this.followUpNote = followUpNote;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Map<String, FieldMeta> getFieldMeta() {
        return fieldMeta;
    }

    public void setFieldMeta(Map<String, FieldMeta> fieldMeta) {
        this.fieldMeta = fieldMeta;
    }

    public Map<String, Object> getRawExtraction() {
        return rawExtraction;
    }

    public void setRawExtraction(Map<String, Object> rawExtraction) {
        this.rawExtraction = rawExtraction;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
