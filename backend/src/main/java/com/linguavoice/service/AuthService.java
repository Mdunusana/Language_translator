package com.linguavoice.service;

import com.linguavoice.dto.auth.AuthResponse;
import com.linguavoice.dto.auth.LoginRequest;
import com.linguavoice.dto.auth.RegisterRequest;
import com.linguavoice.dto.user.UserResponse;
import com.linguavoice.entity.User;
import com.linguavoice.entity.UserPreference;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import com.linguavoice.security.JwtTokenService;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account creation and sign-in. */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";
    static final String EMAIL_TAKEN = "An account with this email address already exists.";

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    /**
     * Hash compared against when the email is unknown, so a failed login takes the same time whether or
     * not the account exists (prevents discovering registered emails by timing).
     */
    private final String timingEqualizerHash;

    public AuthService(UserRepository userRepository, UserPreferenceRepository preferenceRepository,
                       PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.timingEqualizerHash = passwordEncoder.encode("linguavoice-timing-equalizer");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.invalidField("confirmPassword", "Passwords do not match.");
        }
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("email", EMAIL_TAKEN);
        }

        User user;
        try {
            user = userRepository.saveAndFlush(
                    new User(request.fullName().trim(), email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException raceWithAnotherRegistration) {
            throw ApiException.conflict("email", EMAIL_TAKEN);
        }
        UserPreference preference = preferenceRepository.save(UserPreference.defaultsFor(user));
        return issueToken(user, preference);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(Emails.normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        UserPreference preference = preferenceRepository.findByUserId(user.get().getId()).orElse(null);
        return issueToken(user.get(), preference);
    }

    private AuthResponse issueToken(User user, UserPreference preference) {
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return AuthResponse.bearer(token.value(), token.expiresAt(), UserResponse.from(user, preference));
    }
}
