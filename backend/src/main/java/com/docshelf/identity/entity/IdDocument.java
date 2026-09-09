// Typed fields for identity documents; number and address are encrypted per field
package com.docshelf.identity.entity;

import com.docshelf.extract.FieldMeta;
import com.docshelf.member.entity.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "id_document")
public class IdDocument {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "member_id")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type")
    private IdType idType;

    @Column(name = "number_encrypted")
    private byte[] numberEncrypted;

    @Column(name = "number_masked")
    private String numberMasked;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "number_hmac", length = 64)
    private String numberHmac;

    @Column(name = "name_on_document")
    private String nameOnDocument;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "year_of_birth")
    private Integer yearOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuing_authority")
    private String issuingAuthority;

    @Column(name = "address_encrypted")
    private byte[] addressEncrypted;

    @Column(name = "address_masked")
    private String addressMasked;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_meta")
    private Map<String, FieldMeta> fieldMeta = new LinkedHashMap<>();

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

    public IdType getIdType() {
        return idType;
    }

    public void setIdType(IdType idType) {
        this.idType = idType;
    }

    public byte[] getNumberEncrypted() {
        return numberEncrypted;
    }

    public void setNumberEncrypted(byte[] numberEncrypted) {
        this.numberEncrypted = numberEncrypted;
    }

    public String getNumberMasked() {
        return numberMasked;
    }

    public void setNumberMasked(String numberMasked) {
        this.numberMasked = numberMasked;
    }

    public String getNumberHmac() {
        return numberHmac;
    }

    public void setNumberHmac(String numberHmac) {
        this.numberHmac = numberHmac;
    }

    public String getNameOnDocument() {
        return nameOnDocument;
    }

    public void setNameOnDocument(String nameOnDocument) {
        this.nameOnDocument = nameOnDocument;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public Integer getYearOfBirth() {
        return yearOfBirth;
    }

    public void setYearOfBirth(Integer yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
    }

    public byte[] getAddressEncrypted() {
        return addressEncrypted;
    }

    public void setAddressEncrypted(byte[] addressEncrypted) {
        this.addressEncrypted = addressEncrypted;
    }

    public String getAddressMasked() {
        return addressMasked;
    }

    public void setAddressMasked(String addressMasked) {
        this.addressMasked = addressMasked;
    }

    public Map<String, FieldMeta> getFieldMeta() {
        return fieldMeta;
    }

    public void setFieldMeta(Map<String, FieldMeta> fieldMeta) {
        this.fieldMeta = fieldMeta;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
