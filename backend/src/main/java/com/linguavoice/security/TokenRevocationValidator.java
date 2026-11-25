package com.linguavoice.security;

import com.linguavoice.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects otherwise-valid tokens that must no longer work:
 * <ul>
 *   <li>the account has been deleted;</li>
 *   <li>the token was issued before the user's last password change (so changing the password signs out
 *       every other session, e.g. a stolen token).</li>
 * </ul>
 * Costs one primary-key lookup of two columns per authenticated request.
 */
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult REVOKED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token is no longer valid.", null));

    private final UserRepository userRepository;

    public TokenRevocationValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            return REVOKED;
        }
        Optional<UserRepository.TokenState> state = userRepository.findTokenStateById(userId);
        if (state.isEmpty()) {
            return REVOKED;
        }
        LocalDateTime changedAt = state.get().getPasswordChangedAt();
        Instant issuedAt = jwt.getIssuedAt();
        if (changedAt != null) {
            // iat has whole-second precision, so compare at second precision: a token issued in the same second
            // as the change (the fresh token returned by the change itself) stays valid.
            long changedSecond = changedAt.toEpochSecond(ZoneOffset.UTC);
            if (issuedAt == null || issuedAt.getEpochSecond() < changedSecond) {
                return REVOKED;
            }
        }
        return OAuth2TokenValidatorResult.success();
    }
}
