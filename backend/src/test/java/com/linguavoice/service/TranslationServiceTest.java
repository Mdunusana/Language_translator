package com.linguavoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linguavoice.dto.translation.TextTranslationRequest;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import com.linguavoice.entity.User;
import com.linguavoice.exception.ApiException;
import com.linguavoice.exception.RateLimitExceededException;
import com.linguavoice.integration.AiProviderException;
import com.linguavoice.integration.translation.TranslationProvider;
import com.linguavoice.integration.translation.TranslationResult;
import com.linguavoice.repository.TranslationRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.RateLimiter;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock private TranslationProvider provider;
    @Mock private LanguageService languageService;
    @Mock private TranslationRepository translationRepository;
    @Mock private UserRepository userRepository;
    @Mock private RateLimiter rateLimiter;

    private TranslationService service;
    private final User user = new User("Ada", "ada@example.com", "$2a$hash");

    @BeforeEach
    void setUp() {
        service = new TranslationService(provider, languageService, translationRepository, userRepository, rateLimiter);
        ReflectionTestUtils.setField(user, "id", 7L);
        lenient().when(languageService.translatableLanguageCodes())
                .thenReturn(new LinkedHashSet<>(List.of("en", "af", "zu", "fr")));
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(translationRepository.save(any(Translation.class))).thenAnswer(invocation -> {
            Translation t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 99L);
            ReflectionTestUtils.setField(t, "createdAt", LocalDateTime.now());
            return t;
        });
    }

    @Test
    @DisplayName("translates with the given source and saves a TEXT record for the user")
    void translatesAndSaves() {
        when(provider.translate("Hello", "en", "af")).thenReturn(new TranslationResult("Hallo", null));

        TranslationResponse response = service.translateText(7L, new TextTranslationRequest("  Hello ", "en", "af"));

        ArgumentCaptor<Translation> saved = ArgumentCaptor.forClass(Translation.class);
        verify(translationRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getOriginalText()).isEqualTo("Hello");
        assertThat(saved.getValue().getTranslatedText()).isEqualTo("Hallo");
        assertThat(saved.getValue().getTranslationType()).isEqualTo(TranslationType.TEXT);
        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.sourceDetected()).isFalse();
        verify(rateLimiter).consumeAiRequest(7L);
    }

    @Test
    @DisplayName("auto-detect passes no source to the provider and stores the detected language")
    void autoDetect() {
        when(provider.translate(anyString(), isNull(), anyString())).thenReturn(new TranslationResult("Hello", "fr"));

        TranslationResponse response = service.translateText(7L, new TextTranslationRequest("Bonjour", "auto", "en"));

        assertThat(response.sourceLanguage()).isEqualTo("fr");
        assertThat(response.sourceDetected()).isTrue();
    }

    @Test
    @DisplayName("stores 'und' when the provider cannot detect the language")
    void undeterminedSource() {
        when(provider.translate(anyString(), isNull(), anyString())).thenReturn(new TranslationResult("?", null));

        assertThat(service.translateText(7L, new TextTranslationRequest("???", null, "en")).sourceLanguage())
                .isEqualTo("und");
    }

    @Test
    @DisplayName("rejects unsupported languages and identical source/target before calling the provider")
    void validatesLanguages() {
        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "en", "ko")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("targetLanguage"));
        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "ko", "en")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("sourceLanguage"));
        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "en", "en")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("targetLanguage"));

        verify(provider, never()).translate(any(), any(), any());
        verify(rateLimiter, never()).consumeAiRequest(anyLong());
    }

    @Test
    @DisplayName("a provider failure propagates and nothing is saved")
    void providerFailureSavesNothing() {
        when(provider.translate(anyString(), anyString(), anyString()))
                .thenThrow(AiProviderException.failed(TranslationProvider.FEATURE));

        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "en", "af")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(translationRepository, never()).save(any());
    }

    @Test
    @DisplayName("when the rate limit is exceeded the provider is not called")
    void rateLimitStopsBeforeProvider() {
        doThrow(new RateLimitExceededException(5)).when(rateLimiter).consumeAiRequest(7L);

        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "en", "af")))
                .isInstanceOf(RateLimitExceededException.class);
        verify(provider, never()).translate(any(), any(), any());
    }

    @Test
    @DisplayName("without a configured provider the request fails with 503")
    void notConfigured() {
        when(languageService.translatableLanguageCodes())
                .thenThrow(AiProviderException.notConfigured(TranslationProvider.FEATURE));

        assertThatThrownBy(() -> service.translateText(7L, new TextTranslationRequest("Hi", "en", "af")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
