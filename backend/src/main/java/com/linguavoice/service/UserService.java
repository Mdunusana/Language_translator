package com.linguavoice.service;

import com.linguavoice.dto.user.ChangePasswordRequest;
import com.linguavoice.dto.user.UpdateProfileRequest;
import com.linguavoice.dto.user.UserResponse;
import com.linguavoice.entity.User;
import com.linguavoice.exception.ApiException;
import com.linguavoice.repository.UserPreferenceRepository;
import com.linguavoice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile and password management for the signed-in user. Every method works only on {@code userId}. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserPreferenceRepository preferenceRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
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
    public void changePassword(long userId, ChangePasswordRequest request) {
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
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
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
