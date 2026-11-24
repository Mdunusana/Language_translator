package com.linguavoice.controller;

import com.linguavoice.dto.user.ChangePasswordRequest;
import com.linguavoice.dto.user.UpdatePreferencesRequest;
import com.linguavoice.dto.user.UpdateProfileRequest;
import com.linguavoice.dto.user.UserResponse;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account. There is deliberately no {@code /users/{id}} route: the user is
 * always taken from the token, so one user cannot address another user's profile.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public UserResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(CurrentUser.id(jwt));
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(jwt), request);
    }

    @PutMapping("/preferences")
    public UserResponse updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UpdatePreferencesRequest request) {
        return userService.updatePreferences(CurrentUser.id(jwt), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.id(jwt), request);
    }
}
