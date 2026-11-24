package com.linguavoice.service;

import java.util.List;

/**
 * The languages LinguaVoice offers, in display order. A language is only shown to users when the configured
 * provider actually supports it (see {@link LanguageService}); this list never promises support by itself.
 * Codes are LinguaVoice codes (BCP-47 base language), mapped to provider codes inside each adapter.
 */
public final class LanguageCatalog {

    public record Language(String code, String name, String nativeName) {
    }

    public static final List<Language> LANGUAGES = List.of(
            new Language("en", "English", "English"),
            new Language("af", "Afrikaans", "Afrikaans"),
            new Language("zu", "isiZulu", "isiZulu"),
            new Language("xh", "isiXhosa", "isiXhosa"),
            new Language("es", "Spanish", "Español"),
            new Language("fr", "French", "Français"),
            new Language("pt", "Portuguese", "Português"),
            new Language("de", "German", "Deutsch"),
            new Language("zh", "Mandarin Chinese", "中文（简体）"),
            new Language("ar", "Arabic", "العربية"),
            new Language("ja", "Japanese", "日本語"),
            new Language("sw", "Swahili", "Kiswahili"));

    /** Value of {@code source_language} when the provider could not tell which language the text was in. */
    public static final String UNDETERMINED = "und";

    private LanguageCatalog() {
    }
}
