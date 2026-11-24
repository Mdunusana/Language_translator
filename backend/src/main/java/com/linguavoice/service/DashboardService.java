package com.linguavoice.service;

import com.linguavoice.dto.dashboard.StatisticsResponse;
import com.linguavoice.dto.dashboard.StatisticsResponse.DailyCount;
import com.linguavoice.dto.dashboard.StatisticsResponse.LanguageUsage;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.TranslationRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-user statistics for the dashboard. Everything is computed from the caller's own records only. */
@Service
public class DashboardService {

    public static final int DEFAULT_DAYS = 14;
    public static final int MAX_DAYS = 90;
    private static final int TOP_LANGUAGES = 5;

    private final TranslationRepository translationRepository;
    private final Clock clock;

    public DashboardService(TranslationRepository translationRepository, Clock clock) {
        this.translationRepository = translationRepository;
        this.clock = clock;
    }

    /**
     * @param timeZone IANA zone (e.g. {@code Africa/Johannesburg}) used to decide which day a translation
     *                 belongs to; timestamps are stored in UTC
     */
    @Transactional(readOnly = true)
    public StatisticsResponse statistics(long userId, String timeZone, int days) {
        ZoneId zone = parseZone(timeZone);
        if (days < 1 || days > MAX_DAYS) {
            throw ApiException.invalidField("days", "Days must be between 1 and " + MAX_DAYS + ".");
        }
        LocalDateTime nowUtc = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));

        long total = translationRepository.countByUserId(userId);
        long voice = translationRepository.countByUserIdAndTranslationType(userId, TranslationType.VOICE);
        long text = translationRepository.countByUserIdAndTranslationType(userId, TranslationType.TEXT);
        long lastSevenDays = translationRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                userId, nowUtc.minus(Duration.ofDays(7)));

        return new StatisticsResponse(total, text, voice, lastSevenDays,
                topLanguages(userId), dailyActivity(userId, zone, days), zone.getId());
    }

    @Transactional(readOnly = true)
    public List<TranslationResponse> recentActivity(long userId, int limit) {
        if (limit < 1 || limit > 20) {
            throw ApiException.invalidField("limit", "Limit must be between 1 and 20.");
        }
        return translationRepository.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .limit(limit)
                .map(translation -> TranslationResponse.from(translation, false))
                .toList();
    }

    /** Counts each language once per use as a source and once per use as a target; 'und' is ignored. */
    private List<LanguageUsage> topLanguages(long userId) {
        Map<String, Long> counts = new HashMap<>();
        for (List<Object[]> rows : List.of(translationRepository.countBySourceLanguage(userId),
                translationRepository.countByTargetLanguage(userId))) {
            for (Object[] row : rows) {
                String code = (String) row[0];
                if (!LanguageCatalog.UNDETERMINED.equals(code)) {
                    counts.merge(code, ((Number) row[1]).longValue(), Long::sum);
                }
            }
        }
        long all = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_LANGUAGES)
                .map(entry -> new LanguageUsage(entry.getKey(), LanguageService.nameOf(entry.getKey()),
                        entry.getValue(), all == 0 ? 0 : (int) Math.round(entry.getValue() * 100.0 / all)))
                .toList();
    }

    /** Day buckets in the caller's zone, oldest first, zero-filled. */
    private List<DailyCount> dailyActivity(long userId, ZoneId zone, int days) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstDay = today.minusDays(days - 1L);
        LocalDateTime sinceUtc = firstDay.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            buckets.put(firstDay.plusDays(i), 0L);
        }
        for (LocalDateTime createdUtc : translationRepository.findCreatedAtSince(userId, sinceUtc)) {
            LocalDate day = createdUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
            buckets.computeIfPresent(day, (key, count) -> count + 1);
        }
        List<DailyCount> result = new ArrayList<>(days);
        buckets.forEach((date, count) -> result.add(new DailyCount(date, count)));
        return result;
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw ApiException.invalidField("timezone", "Unknown time zone.");
        }
    }
}
