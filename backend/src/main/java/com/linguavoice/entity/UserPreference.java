package com.linguavoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Per-user defaults for the translator. Exactly one row per user ({@code user_preferences}).
 * {@link DynamicUpdate} writes only changed columns (see {@link User}).
 */
@Entity
@Table(name = "user_preferences")
@DynamicUpdate
public class UserPreference {

    public static final String DEFAULT_SOURCE_LANGUAGE = "en";
    public static final String DEFAULT_TARGET_LANGUAGE = "af";
    public static final BigDecimal DEFAULT_SPEECH_SPEED = new BigDecimal("1.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "preferred_source_language", nullable = false, length = 16)
    private String preferredSourceLanguage;

    @Column(name = "preferred_target_language", nullable = false, length = 16)
    private String preferredTargetLanguage;

    @Column(name = "speech_speed", nullable = false, precision = 3, scale = 2)
    private BigDecimal speechSpeed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreference() {
        // for JPA
    }

    public static UserPreference defaultsFor(User user) {
        UserPreference preference = new UserPreference();
        preference.user = user;
        preference.preferredSourceLanguage = DEFAULT_SOURCE_LANGUAGE;
        preference.preferredTargetLanguage = DEFAULT_TARGET_LANGUAGE;
        preference.speechSpeed = DEFAULT_SPEECH_SPEED;
        return preference;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPreferredSourceLanguage() {
        return preferredSourceLanguage;
    }

    public void setPreferredSourceLanguage(String preferredSourceLanguage) {
        this.preferredSourceLanguage = preferredSourceLanguage;
    }

    public String getPreferredTargetLanguage() {
        return preferredTargetLanguage;
    }

    public void setPreferredTargetLanguage(String preferredTargetLanguage) {
        this.preferredTargetLanguage = preferredTargetLanguage;
    }

    public BigDecimal getSpeechSpeed() {
        return speechSpeed;
    }

    public void setSpeechSpeed(BigDecimal speechSpeed) {
        this.speechSpeed = speechSpeed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
