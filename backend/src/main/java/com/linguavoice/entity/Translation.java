package com.linguavoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One saved translation in a user's history ({@code translations}). Records are immutable once created.
 * Raw audio is never stored, only the recognised and translated text.
 */
@Entity
@Table(name = "translations")
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    @Column(name = "source_language", nullable = false, length = 16)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 16)
    private String targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "translation_type", nullable = false, length = 10)
    private TranslationType translationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Translation() {
        // for JPA
    }

    public Translation(User user, String originalText, String translatedText,
                       String sourceLanguage, String targetLanguage, TranslationType translationType) {
        this.user = user;
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.translationType = translationType;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public TranslationType getTranslationType() {
        return translationType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
