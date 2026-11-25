package com.linguavoice.service;

import com.linguavoice.dto.user.ChangePasswordRequest;
import com.linguavoice.dto.user.UpdatePreferencesRequest;
import com.linguavoice.dto.user.UpdateProfileRequest;
import com.linguavoice.dto.user.UserResponse;
import com.linguavoice.entity.User;
import com.linguavoice.entity.UserPreference;
import com.linguavoice.dto.auth.AuthResponse;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.JwtTokenService;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile and password management for the signed-in user. Every method works only on {@code userId}. */
@Service
public class UserService {

    private static final String AUTO_DETECT = "auto";

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final Clock clock;

    public UserService(UserRepository userRepository, UserPreferenceRepository preferenceRepository,
                       PasswordEncoder passwordEncoder, JwtTokenService tokenService, Clock clock) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(long userId) {
        return toResponse(loadUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = loadUser(userId);
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw ApiException.conflict("email", AuthService.EMAIL_TAKEN);
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException raceWithAnotherUpdate) {
            throw ApiException.conflict("email", AuthService.EMAIL_TAKEN);
        }
        return toResponse(user);
    }

    @Transactional
    /**
     * Changes the password and signs out every other session: tokens issued before now stop working.
     * Returns a fresh token so the caller's own session continues.
     */
    public AuthResponse changePassword(long userId, ChangePasswordRequest request) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.invalidField("currentPassword", "Current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw ApiException.invalidField("confirmNewPassword", "Passwords do not match.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw ApiException.invalidField("newPassword", "New password must be different from the current one.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        userRepository.saveAndFlush(user);
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return AuthResponse.bearer(token.value(), token.expiresAt(), toResponse(user));
    }

    /** Default languages and speaking speed used by the translator. */
    @Transactional
    public UserResponse updatePreferences(long userId, UpdatePreferencesRequest request) {
        User user = loadUser(userId);
        String source = request.preferredSourceLanguage();
        String target = request.preferredTargetLanguage();
        if (!AUTO_DETECT.equals(source) && !LanguageCatalog.contains(source)) {
            throw ApiException.invalidField("preferredSourceLanguage", "Unknown language.");
        }
        if (!LanguageCatalog.contains(target)) {
            throw ApiException.invalidField("preferredTargetLanguage", "Unknown language.");
        }
        if (source.equals(target)) {
            throw ApiException.invalidField("preferredTargetLanguage",
                    "Choose a target language that is different from the source language.");
        }

        UserPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserPreference.defaultsFor(user));
        preference.setPreferredSourceLanguage(source);
        preference.setPreferredTargetLanguage(target);
        preference.setSpeechSpeed(request.speechSpeed().setScale(2, RoundingMode.HALF_UP));
        preferenceRepository.save(preference);
        return UserResponse.from(user, preference);
    }

    /**
     * A valid token whose user no longer exists is treated as unauthenticated, so the frontend signs out.
     */
    private User loadUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please sign in again."));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, preferenceRepository.findByUserId(user.getId()).orElse(null));
    }
}
