// Computed 1y / 3y returns vs benchmark per scheme (composite key isin + as_of_date)
package com.docshelf.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scheme_performance")
@IdClass(SchemePerformance.Key.class)
public class SchemePerformance {

    public static class Key implements Serializable {
        private String isin;
        private LocalDate asOfDate;

        public Key() {
        }

        public Key(String isin, LocalDate asOfDate) {
            this.isin = isin;
            this.asOfDate = asOfDate;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(isin, k.isin) && Objects.equals(asOfDate, k.asOfDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isin, asOfDate);
        }
    }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "isin", length = 12)
    private String isin;

    @Id
    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "ret_1y", precision = 8, scale = 5)
    private BigDecimal ret1y;

    @Column(name = "cagr_3y", precision = 8, scale = 5)
    private BigDecimal cagr3y;

    @Column(name = "bench_ret_1y", precision = 8, scale = 5)
    private BigDecimal benchRet1y;

    @Column(name = "bench_cagr_3y", precision = 8, scale = 5)
    private BigDecimal benchCagr3y;

    @Column(name = "benchmark_name")
    private String benchmarkName;

    @Enumerated(EnumType.STRING)
    @Column(name = "benchmark_basis", nullable = false)
    private BenchmarkBasis benchmarkBasis;

    @Column(name = "peer_count")
    private Integer peerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "underperformed", nullable = false)
    private Underperformed underperformed;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getRet1y() {
        return ret1y;
    }

    public void setRet1y(BigDecimal ret1y) {
        this.ret1y = ret1y;
    }

    public BigDecimal getCagr3y() {
        return cagr3y;
    }

    public void setCagr3y(BigDecimal cagr3y) {
        this.cagr3y = cagr3y;
    }

    public BigDecimal getBenchRet1y() {
        return benchRet1y;
    }

    public void setBenchRet1y(BigDecimal benchRet1y) {
        this.benchRet1y = benchRet1y;
    }

    public BigDecimal getBenchCagr3y() {
        return benchCagr3y;
    }

    public void setBenchCagr3y(BigDecimal benchCagr3y) {
        this.benchCagr3y = benchCagr3y;
    }

    public String getBenchmarkName() {
        return benchmarkName;
    }

    public void setBenchmarkName(String benchmarkName) {
        this.benchmarkName = benchmarkName;
    }

    public BenchmarkBasis getBenchmarkBasis() {
        return benchmarkBasis;
    }

    public void setBenchmarkBasis(BenchmarkBasis benchmarkBasis) {
        this.benchmarkBasis = benchmarkBasis;
    }

    public Integer getPeerCount() {
        return peerCount;
    }

    public void setPeerCount(Integer peerCount) {
        this.peerCount = peerCount;
    }

    public Underperformed getUnderperformed() {
        return underperformed;
    }

    public void setUnderperformed(Underperformed underperformed) {
        this.underperformed = underperformed;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
