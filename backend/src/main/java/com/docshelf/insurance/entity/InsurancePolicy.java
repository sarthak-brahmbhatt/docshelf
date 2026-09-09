// Typed fields for INSURANCE_POLICY documents
package com.docshelf.insurance.entity;

import com.docshelf.extract.FieldMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "insurance_policy")
public class InsurancePolicy {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "insurer")
    private String insurer;

    @Column(name = "policy_no")
    private String policyNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type")
    private PolicyType policyType;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "sum_assured", precision = 14, scale = 2)
    private BigDecimal sumAssured;

    @Column(name = "premium_amount", precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_frequency")
    private PremiumFrequency premiumFrequency;

    @Column(name = "premium_due_date")
    private LocalDate premiumDueDate;

    @Column(name = "policy_start")
    private LocalDate policyStart;

    @Column(name = "policy_expiry")
    private LocalDate policyExpiry;

    @Column(name = "nominee_name")
    private String nomineeName;

    @Column(name = "nominee_relation")
    private String nomineeRelation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insured_members")
    private List<Map<String, Object>> insuredMembers = new ArrayList<>();

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

    public String getInsurer() {
        return insurer;
    }

    public void setInsurer(String insurer) {
        this.insurer = insurer;
    }

    public String getPolicyNo() {
        return policyNo;
    }

    public void setPolicyNo(String policyNo) {
        this.policyNo = policyNo;
    }

    public PolicyType getPolicyType() {
        return policyType;
    }

    public void setPolicyType(PolicyType policyType) {
        this.policyType = policyType;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getSumAssured() {
        return sumAssured;
    }

    public void setSumAssured(BigDecimal sumAssured) {
        this.sumAssured = sumAssured;
    }

    public BigDecimal getPremiumAmount() {
        return premiumAmount;
    }

    public void setPremiumAmount(BigDecimal premiumAmount) {
        this.premiumAmount = premiumAmount;
    }

    public PremiumFrequency getPremiumFrequency() {
        return premiumFrequency;
    }

    public void setPremiumFrequency(PremiumFrequency premiumFrequency) {
        this.premiumFrequency = premiumFrequency;
    }

    public LocalDate getPremiumDueDate() {
        return premiumDueDate;
    }

    public void setPremiumDueDate(LocalDate premiumDueDate) {
        this.premiumDueDate = premiumDueDate;
    }

    public LocalDate getPolicyStart() {
        return policyStart;
    }

    public void setPolicyStart(LocalDate policyStart) {
        this.policyStart = policyStart;
    }

    public LocalDate getPolicyExpiry() {
        return policyExpiry;
    }

    public void setPolicyExpiry(LocalDate policyExpiry) {
        this.policyExpiry = policyExpiry;
    }

    public String getNomineeName() {
        return nomineeName;
    }

    public void setNomineeName(String nomineeName) {
        this.nomineeName = nomineeName;
    }

    public String getNomineeRelation() {
        return nomineeRelation;
    }

    public void setNomineeRelation(String nomineeRelation) {
        this.nomineeRelation = nomineeRelation;
    }

    public List<Map<String, Object>> getInsuredMembers() {
        return insuredMembers;
    }

    public void setInsuredMembers(List<Map<String, Object>> insuredMembers) {
        this.insuredMembers = insuredMembers;
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
