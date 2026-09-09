// Mutual fund scheme reference data keyed by ISIN (AMFI / CAS / user)
package com.docshelf.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scheme_master")
public class SchemeMaster {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "isin", length = 12)
    private String isin;

    @Column(name = "amfi_code")
    private String amfiCode;

    @Column(name = "scheme_name")
    private String schemeName;

    @Column(name = "amc")
    private String amc;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme_type")
    private SchemeType schemeType;

    @Column(name = "category")
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_bucket")
    private CategoryBucket categoryBucket;

    @Column(name = "benchmark_index")
    private String benchmarkIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type")
    private OptionType optionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private SchemeSource source = SchemeSource.AMFI;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getAmfiCode() {
        return amfiCode;
    }

    public void setAmfiCode(String amfiCode) {
        this.amfiCode = amfiCode;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getAmc() {
        return amc;
    }

    public void setAmc(String amc) {
        this.amc = amc;
    }

    public SchemeType getSchemeType() {
        return schemeType;
    }

    public void setSchemeType(SchemeType schemeType) {
        this.schemeType = schemeType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public CategoryBucket getCategoryBucket() {
        return categoryBucket;
    }

    public void setCategoryBucket(CategoryBucket categoryBucket) {
        this.categoryBucket = categoryBucket;
    }

    public String getBenchmarkIndex() {
        return benchmarkIndex;
    }

    public void setBenchmarkIndex(String benchmarkIndex) {
        this.benchmarkIndex = benchmarkIndex;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(OptionType optionType) {
        this.optionType = optionType;
    }

    public SchemeSource getSource() {
        return source;
    }

    public void setSource(SchemeSource source) {
        this.source = source;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
