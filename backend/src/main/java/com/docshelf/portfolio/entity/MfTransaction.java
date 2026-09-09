// A CAS transaction line, deduplicated by dedupe_key
package com.docshelf.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mf_transaction")
public class MfTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "folio_id")
    private UUID folioId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "isin", length = 12)
    private String isin;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type")
    private TxnType txnType;

    @Column(name = "amount", precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(name = "units", precision = 18, scale = 3)
    private BigDecimal units;

    @Column(name = "nav", precision = 12, scale = 4)
    private BigDecimal nav;

    @Column(name = "balance_units", precision = 18, scale = 3)
    private BigDecimal balanceUnits;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dedupe_key", length = 64)
    private String dedupeKey;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getFolioId() {
        return folioId;
    }

    public void setFolioId(UUID folioId) {
        this.folioId = folioId;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TxnType getTxnType() {
        return txnType;
    }

    public void setTxnType(TxnType txnType) {
        this.txnType = txnType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getUnits() {
        return units;
    }

    public void setUnits(BigDecimal units) {
        this.units = units;
    }

    public BigDecimal getNav() {
        return nav;
    }

    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }

    public BigDecimal getBalanceUnits() {
        return balanceUnits;
    }

    public void setBalanceUnits(BigDecimal balanceUnits) {
        this.balanceUnits = balanceUnits;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(UUID sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }
}
