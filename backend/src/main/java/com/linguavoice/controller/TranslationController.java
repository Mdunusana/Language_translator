package com.linguavoice.controller;

import com.linguavoice.dto.translation.TextTranslationRequest;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.exception.ApiException;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.TranslationService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Translation endpoints. History (Phase 6) is added here later. */
@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    /** Translates text and saves it to the caller's history. Returns 201 with the saved record. */
    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public TranslationResponse translateText(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody TextTranslationRequest request) {
        return translationService.translateText(CurrentUser.id(jwt), request);
    }

    /**
     * Multipart upload: {@code audio} (16 kHz mono 16-bit PCM WAV, at most 60 s), {@code sourceLanguage}
     * (the language spoken) and {@code targetLanguage}. Returns 201 with the saved record, whose
     * {@code originalText} is the recognised speech. The audio itself is never stored.
     */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TranslationResponse translateVoice(@AuthenticationPrincipal Jwt jwt,
                                              @RequestPart("audio") MultipartFile audio,
                                              @RequestParam("sourceLanguage") String sourceLanguage,
                                              @RequestParam("targetLanguage") String targetLanguage) throws IOException {
        if (audio.isEmpty()) {
            throw ApiException.invalidField("audio", "The recording is empty.");
        }
        return translationService.translateVoice(CurrentUser.id(jwt), audio.getBytes(),
                sourceLanguage.trim(), targetLanguage.trim());
    }
}
