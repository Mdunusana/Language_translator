package com.linguavoice.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/dashboard/statistics}.
 *
 * @param lastSevenDays  translations in the last 7 days (rolling, now − 7 × 24 h)
 * @param topLanguages   most used languages (as source or target), most used first, at most 5
 * @param dailyActivity  one entry per day for the last {@code days} days in the requested time zone, oldest first,
 *                       including days with zero translations
 */
public record StatisticsResponse(
        long totalTranslations,
        long textTranslations,
        long voiceTranslations,
        long lastSevenDays,
        List<LanguageUsage> topLanguages,
        List<DailyCount> dailyActivity,
        String timeZone) {

    /** @param share percentage of all language uses (sources + targets), 0–100 */
    public record LanguageUsage(String code, String name, long count, int share) {
    }

    public record DailyCount(LocalDate date, long count) {
    }
}
