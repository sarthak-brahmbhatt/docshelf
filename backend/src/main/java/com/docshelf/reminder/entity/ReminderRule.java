// Data-driven reminder rule (seeded by V1__init.sql, editable in the UI)
package com.docshelf.reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reminder_rule")
public class ReminderRule {

    @Id
    @Column(name = "rule_key")
    private String ruleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private ReminderSourceType sourceType;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "date_field")
    private String dateField;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "offsets_days")
    private List<Integer> offsetsDays = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "channels")
    private List<String> channels = new ArrayList<>();

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "description")
    private String description;

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public ReminderSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ReminderSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDateField() {
        return dateField;
    }

    public void setDateField(String dateField) {
        this.dateField = dateField;
    }

    public List<Integer> getOffsetsDays() {
        return offsetsDays;
    }

    public void setOffsetsDays(List<Integer> offsetsDays) {
        this.offsetsDays = offsetsDays;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
