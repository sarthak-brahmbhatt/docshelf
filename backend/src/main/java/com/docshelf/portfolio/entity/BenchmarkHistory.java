// Benchmark index level per day (composite key index_name + price_date), CSV-imported
package com.docshelf.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "benchmark_history")
@IdClass(BenchmarkHistory.Key.class)
public class BenchmarkHistory {

    public static class Key implements Serializable {
        private String indexName;
        private LocalDate priceDate;

        public Key() {
        }

        public Key(String indexName, LocalDate priceDate) {
            this.indexName = indexName;
            this.priceDate = priceDate;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(indexName, k.indexName) && Objects.equals(priceDate, k.priceDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(indexName, priceDate);
        }
    }

    @Id
    @Column(name = "index_name")
    private String indexName;

    @Id
    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "value", nullable = false, precision = 16, scale = 4)
    private BigDecimal value;

    @Column(name = "source", nullable = false)
    private String source = "CSV_IMPORT";

    public BenchmarkHistory() {
    }

    public BenchmarkHistory(String indexName, LocalDate priceDate, BigDecimal value, String source) {
        this.indexName = indexName;
        this.priceDate = priceDate;
        this.value = value;
        this.source = source;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
