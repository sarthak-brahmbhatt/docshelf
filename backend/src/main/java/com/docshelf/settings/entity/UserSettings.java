// Single-row (id = 1) server-side user settings
package com.docshelf.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "reminder_email")
    private String reminderEmail;

    @Column(name = "owner_whatsapp")
    private String ownerWhatsapp;

    @Column(name = "voice_language")
    private String voiceLanguage = "en-IN";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getReminderEmail() {
        return reminderEmail;
    }

    public void setReminderEmail(String reminderEmail) {
        this.reminderEmail = reminderEmail;
    }

    public String getOwnerWhatsapp() {
        return ownerWhatsapp;
    }

    public void setOwnerWhatsapp(String ownerWhatsapp) {
        this.ownerWhatsapp = ownerWhatsapp;
    }

    public String getVoiceLanguage() {
        return voiceLanguage;
    }

    public void setVoiceLanguage(String voiceLanguage) {
        this.voiceLanguage = voiceLanguage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
