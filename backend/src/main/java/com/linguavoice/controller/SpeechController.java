package com.linguavoice.controller;

import com.linguavoice.dto.speech.SynthesizeRequest;
import com.linguavoice.dto.speech.VoicesResponse;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.SpeechService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Text-to-speech endpoints. */
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    public static final String VOICE_HEADER = "X-LinguaVoice-Voice";

    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    /** Voices available for a language; {@code available=false} tells the browser to use its own voices. */
    @GetMapping("/voices")
    public VoicesResponse voices(@RequestParam("language") String language) {
        return speechService.voices(language.trim());
    }

    /** Returns MP3 audio ({@code audio/mpeg}). The voice actually used is reported in {@value #VOICE_HEADER}. */
    @PostMapping(value = "/synthesize", produces = {"audio/mpeg", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<byte[]> synthesize(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody SynthesizeRequest request) {
        SpeechService.SynthesizedSpeech speech = speechService.synthesize(CurrentUser.id(jwt), request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .header(VOICE_HEADER, speech.voice().id())
                .body(speech.audio());
    }
}
