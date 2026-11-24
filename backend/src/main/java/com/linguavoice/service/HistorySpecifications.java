package com.linguavoice.service;

import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Type-safe, parameterised history filters (Criteria API: no SQL is built from strings, so user input
 * cannot inject SQL). The owner condition is always applied.
 */
final class HistorySpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private HistorySpecifications() {
    }

    static Specification<Translation> ownedBy(long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    /** Case-insensitive substring match on the original or translated text. % and _ are matched literally. */
    static Specification<Translation> textContains(String search) {
        String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("originalText")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("translatedText")), pattern, LIKE_ESCAPE));
    }

    /** Translations from or into the language. */
    static Specification<Translation> involvesLanguage(String language) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("sourceLanguage"), language),
                cb.equal(root.get("targetLanguage"), language));
    }

    static Specification<Translation> ofType(TranslationType type) {
        return (root, query, cb) -> cb.equal(root.get("translationType"), type);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
