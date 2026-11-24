package com.linguavoice.service;

import com.linguavoice.dto.PageResponse;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.TranslationRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user's translation history. Records of other users are indistinguishable from records
 * that don't exist: both give 404, so ids can't be probed.
 */
@Service
public class HistoryService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 200;
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TranslationRepository translationRepository;

    public HistoryService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    /**
     * @param search   optional text to look for in the original or translated text
     * @param language optional language code; matches translations from or into it
     * @param type     optional {@code TEXT} or {@code VOICE}
     */
    @Transactional(readOnly = true)
    public PageResponse<TranslationResponse> history(long userId, String search, String language, String type,
                                                     int page, int size) {
        if (page < 0) {
            throw ApiException.invalidField("page", "Page must be 0 or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.invalidField("size", "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        Specification<Translation> spec = HistorySpecifications.ownedBy(userId);
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim();
            if (trimmed.length() > MAX_SEARCH_LENGTH) {
                throw ApiException.invalidField("search", "Search text can be at most " + MAX_SEARCH_LENGTH + " characters.");
            }
            spec = spec.and(HistorySpecifications.textContains(trimmed));
        }
        if (language != null && !language.isBlank()) {
            if (!LANGUAGE_CODE.matcher(language.trim()).matches()) {
                throw ApiException.invalidField("language", "Unknown language.");
            }
            spec = spec.and(HistorySpecifications.involvesLanguage(language.trim()));
        }
        if (type != null && !type.isBlank()) {
            spec = spec.and(HistorySpecifications.ofType(parseType(type)));
        }

        return PageResponse.of(translationRepository.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST))
                .map(translation -> TranslationResponse.from(translation, false)));
    }

    @Transactional(readOnly = true)
    public TranslationResponse get(long userId, long id) {
        return translationRepository.findByIdAndUserId(id, userId)
                .map(translation -> TranslationResponse.from(translation, false))
                .orElseThrow(HistoryService::notFound);
    }

    @Transactional
    public void delete(long userId, long id) {
        if (translationRepository.deleteByIdAndUserId(id, userId) == 0) {
            throw notFound();
        }
    }

    /** Deletes the user's whole history and returns how many records were removed. */
    @Transactional
    public int clear(long userId) {
        return translationRepository.deleteAllByUserId(userId);
    }

    private static TranslationType parseType(String type) {
        try {
            return TranslationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalidField("type", "Type must be TEXT or VOICE.");
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("Translation not found.");
    }
}
