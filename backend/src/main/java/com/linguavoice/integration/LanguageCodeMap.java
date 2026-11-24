package com.linguavoice.integration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts between LinguaVoice language codes and one provider's codes. Only codes that differ need an
 * entry (e.g. LinguaVoice {@code zh} is {@code zh-Hans} on Azure and {@code zh-CN} on Google); every other
 * code passes through unchanged (lower-cased).
 */
public final class LanguageCodeMap {

    private final Map<String, String> appToProvider;
    private final Map<String, String> providerToApp = new HashMap<>();

    public LanguageCodeMap(Map<String, String> appToProvider) {
        this.appToProvider = Map.copyOf(appToProvider);
        appToProvider.forEach((app, provider) -> providerToApp.put(provider.toLowerCase(Locale.ROOT), app));
    }

    public String toProvider(String appCode) {
        return appCode == null ? null : appToProvider.getOrDefault(appCode, appCode);
    }

    public String toApp(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        String normalized = providerCode.toLowerCase(Locale.ROOT);
        return providerToApp.getOrDefault(normalized, normalized);
    }
}
