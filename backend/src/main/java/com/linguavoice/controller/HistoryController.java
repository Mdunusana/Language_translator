package com.linguavoice.controller;

import com.linguavoice.dto.PageResponse;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.HistoryService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's translation history. Ids are numeric-only in the routes, so {@code /history} can never be
 * mistaken for an id. Another user's record returns 404, exactly like a record that doesn't exist.
 */
@RestController
@RequestMapping("/api/translations")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /** Newest first. All filters are optional and combine with AND. */
    @GetMapping("/history")
    public PageResponse<TranslationResponse> history(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) String search,
                                                     @RequestParam(required = false) String language,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return historyService.history(CurrentUser.id(jwt), search, language, type, page, size);
    }

    @GetMapping("/{id:\\d+}")
    public TranslationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return historyService.get(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        historyService.delete(CurrentUser.id(jwt), id);
    }

    /** Clears the caller's whole history. Returns {@code {"deleted": n}}. */
    @DeleteMapping("/history")
    public Map<String, Integer> clear(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("deleted", historyService.clear(CurrentUser.id(jwt)));
    }
}
