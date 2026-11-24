package com.linguavoice.integration.tts;

/**
 * A synthetic voice offered by the text-to-speech provider.
 *
 * @param id     provider voice identifier, e.g. {@code zu-ZA-ThandoNeural} or {@code af-ZA-Standard-A}
 * @param name   display name, e.g. "Thando"
 * @param locale provider locale, e.g. {@code zu-ZA}
 * @param gender "Female", "Male" or "Neutral"
 */
public record Voice(String id, String name, String locale, String gender) {
}
