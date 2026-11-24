package com.linguavoice.controller;

import com.linguavoice.dto.translation.LanguagesResponse;
import com.linguavoice.service.LanguageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/languages}: languages the configured providers support, with per-feature capabilities. */
@RestController
@RequestMapping("/api/languages")
public class LanguageController {

    private final LanguageService languageService;

    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @GetMapping
    public LanguagesResponse languages() {
        return languageService.listLanguages();
    }
}
