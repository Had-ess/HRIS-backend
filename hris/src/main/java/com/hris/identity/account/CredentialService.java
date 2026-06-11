package com.hris.identity.account;

import com.hris.identity.account.entity.UserCredential;
import com.hris.identity.account.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Password storage and policy. Shared by activation, reset, and change flows
 * so the policy can never diverge between entry points.
 */
@Service
@RequiredArgsConstructor
public class CredentialService {

    public static final int MIN_LENGTH = 10;

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Policy: at least {@value MIN_LENGTH} characters with lower case, upper
     * case, and a digit. Throws with a stable message key the frontend can map.
     */
    public void validatePolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("PASSWORD_POLICY_TOO_SHORT");
        }
        boolean hasLower = rawPassword.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = rawPassword.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLower || !hasUpper || !hasDigit) {
            throw new IllegalArgumentException("PASSWORD_POLICY_COMPLEXITY");
        }
    }

    @Transactional
    public void setPassword(UUID userId, String rawPassword) {
        validatePolicy(rawPassword);
        setPasswordUnchecked(userId, rawPassword);
    }

    /**
     * Policy bypass reserved for trusted seeding (demo data), never user input.
     */
    @Transactional
    public void setPasswordUnchecked(UUID userId, String rawPassword) {
        UserCredential credential = userCredentialRepository.findById(userId)
            .orElseGet(() -> UserCredential.builder().userId(userId).build());
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credential.setPasswordUpdatedAt(Instant.now());
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        userCredentialRepository.save(credential);
    }

    @Transactional(readOnly = true)
    public boolean verify(UUID userId, String rawPassword) {
        return userCredentialRepository.findById(userId)
            .map(credential -> passwordEncoder.matches(rawPassword, credential.getPasswordHash()))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasCredentials(UUID userId) {
        return userCredentialRepository.existsById(userId);
    }
}
