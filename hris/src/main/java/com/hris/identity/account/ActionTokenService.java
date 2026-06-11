package com.hris.identity.account;

import com.hris.identity.account.entity.UserActionToken;
import com.hris.identity.account.repository.UserActionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and consumes single-use account-action tokens (activation, password
 * reset). Raw tokens are 256-bit random values handed out once via email;
 * only their SHA-256 hash is persisted. Issuing a new token invalidates any
 * outstanding token of the same purpose for that user.
 */
@Service
@RequiredArgsConstructor
public class ActionTokenService {

    public static final Duration ACTIVATION_TTL = Duration.ofHours(24);
    public static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);

    private final UserActionTokenRepository userActionTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(UUID userId, UserActionToken.Purpose purpose, Duration ttl) {
        userActionTokenRepository.invalidateOutstanding(userId, purpose, Instant.now());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        userActionTokenRepository.save(UserActionToken.builder()
            .userId(userId)
            .tokenHash(sha256Hex(rawToken))
            .purpose(purpose)
            .expiresAt(Instant.now().plus(ttl))
            .build());

        return rawToken;
    }

    /**
     * Validates and burns the token; returns the owning user id.
     * Failure modes are deliberately indistinguishable to the caller.
     */
    @Transactional
    public UUID consume(String rawToken, UserActionToken.Purpose purpose) {
        UserActionToken token = userActionTokenRepository
            .findByTokenHashAndPurpose(sha256Hex(rawToken), purpose)
            .filter(UserActionToken::isUsable)
            .orElseThrow(() -> new IllegalArgumentException("TOKEN_INVALID_OR_EXPIRED"));

        token.setUsedAt(Instant.now());
        userActionTokenRepository.save(token);
        return token.getUserId();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
