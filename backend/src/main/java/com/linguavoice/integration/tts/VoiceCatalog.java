package com.linguavoice.integration.tts;

import com.linguavoice.service.LanguageCatalog;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups a provider's voices by LinguaVoice language and orders them so the most natural default comes first
 * (e.g. en-US before en-AU, pt-BR to match the Brazilian-Portuguese translations).
 */
final class VoiceCatalog {

    private static final Set<String> CATALOG_CODES = LanguageCatalog.LANGUAGES.stream()
            .map(LanguageCatalog.Language::code)
            .collect(Collectors.toUnmodifiableSet());

    /** Preferred default locale per language; voices in other locales of the language follow. */
    private static final Map<String, String> PREFERRED_LOCALE = Map.of(
            "en", "en-us", "pt", "pt-br", "es", "es-es", "fr", "fr-fr", "de", "de-de",
            "ar", "ar-sa", "sw", "sw-ke", "zh", "zh-cn");

    private VoiceCatalog() {
    }

    /**
     * LinguaVoice code for a provider locale, or null when the app doesn't offer that language.
     * Mandarin is only mainland Simplified (zh-CN / cmn-CN); other Chinese locales (Cantonese, zh-TW) are excluded.
     */
    static String languageOf(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String normalized = locale.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("zh-cn") || normalized.startsWith("cmn-cn") || normalized.startsWith("cmn-hans")) {
            return "zh";
        }
        String language = normalized.split("-")[0];
        if (language.equals("zh") || language.equals("cmn") || language.equals("yue")) {
            return null;
        }
        return CATALOG_CODES.contains(language) ? language : null;
    }

    static Map<String, List<Voice>> group(List<Voice> voices) {
        Map<String, List<Voice>> grouped = voices.stream()
                .filter(voice -> languageOf(voice.locale()) != null)
                .collect(Collectors.groupingBy(voice -> languageOf(voice.locale())));

        Map<String, List<Voice>> ordered = new LinkedHashMap<>();
        grouped.forEach((language, list) -> {
            String preferred = PREFERRED_LOCALE.get(language);
            ordered.put(language, list.stream()
                    .sorted(Comparator
                            .comparing((Voice voice) -> !voice.locale().toLowerCase(Locale.ROOT).equals(preferred))
                            .thenComparing(Voice::locale)
                            .thenComparing(Voice::name))
                    .toList());
        });
        return Map.copyOf(ordered);
    }
}
