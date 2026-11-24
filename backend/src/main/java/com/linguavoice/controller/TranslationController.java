package com.linguavoice.controller;

import com.linguavoice.dto.translation.TextTranslationRequest;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Translation endpoints. Voice translation (Phase 4) and history (Phase 6) are added here later. */
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
}
