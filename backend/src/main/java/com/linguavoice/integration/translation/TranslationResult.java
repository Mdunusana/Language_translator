package com.linguavoice.integration.translation;

/**
 * @param translatedText         the provider's translation
 * @param detectedSourceLanguage LinguaVoice code of the detected source language, or {@code null}
 *                               when the caller specified the source language
 */
public record TranslationResult(String translatedText, String detectedSourceLanguage) {
}
