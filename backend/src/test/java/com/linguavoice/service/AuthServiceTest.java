package com.linguavoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linguavoice.dto.auth.AuthResponse;
import com.linguavoice.dto.auth.LoginRequest;
import com.linguavoice.dto.auth.RegisterRequest;
import com.linguavoice.entity.User;
import com.linguavoice.entity.UserPreference;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.JwtTokenService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link AuthService} with all collaborators mocked (no Spring context, no database). */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPreferenceRepository preferenceRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("linguavoice-timing-equalizer")).thenReturn("$2a$dummy");
        authService = new AuthService(userRepository, preferenceRepository, passwordEncoder, tokenService);
    }

    @Test
    @DisplayName("register normalises the email, hashes the password and creates default preferences")
    void registerHashesAndNormalises() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> persisted(inv.getArgument(0), 7L));
        when(preferenceRepository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenService.issue(any(User.class))).thenReturn(new JwtTokenService.IssuedToken("jwt", Instant.now()));

        AuthResponse response = authService.register(
                new RegisterRequest("  Ada Lovelace ", " Ada@Example.COM", "Secret123", "Secret123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        verify(preferenceRepository).save(any(UserPreference.class));
        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().preferences().preferredTargetLanguage()).isEqualTo("af");
    }

    @Test
    @DisplayName("register refuses an existing email with 409 and saves nothing")
    void registerRejectsDuplicate() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", "ada@example.com", "Secret123", "Secret123")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("register maps a unique-constraint race to 409")
    void registerHandlesRace() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Ada", "ada@example.com", "Secret123", "Secret123")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("login with an unknown email still runs a BCrypt comparison (timing equaliser)")
    void loginUnknownEmailStillHashes() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Secret123")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo(AuthService.INVALID_CREDENTIALS);
                });
        verify(passwordEncoder).matches("Secret123", "$2a$dummy");
        verify(tokenService, never()).issue(any());
    }

    @Test
    @DisplayName("login with a wrong password is rejected and issues no token")
    void loginWrongPassword() {
        User user = persisted(new User("Ada", "ada@example.com", "$2a$real"), 7L);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), eq("$2a$real"))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "Nope12345")))
                .isInstanceOf(ApiException.class);
        verify(tokenService, never()).issue(any());
    }

    @Test
    @DisplayName("login with correct credentials returns a token")
    void loginSuccess() {
        User user = persisted(new User("Ada", "ada@example.com", "$2a$real"), 7L);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$real")).thenReturn(true);
        when(preferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(tokenService.issue(user)).thenReturn(new JwtTokenService.IssuedToken("jwt", Instant.now()));

        AuthResponse response = authService.login(new LoginRequest("ADA@example.com", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    /** Simulates what JPA does on save: assigns an id and timestamps. */
    private static User persisted(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.now());
        return user;
    }
}
