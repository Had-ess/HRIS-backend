package com.hris.auth.service;

import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * GAP-B-02: JIT (Just-In-Time) user provisioning from Keycloak JWT.
 * Maps sub (keycloakId) → users.keycloak_id → local users.id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private static final String SEEDED_KEYCLOAK_PLACEHOLDER_PREFIX = "KC_REPLACE_";

    private final UserRepository userRepository;

    /**
     * Given a JWT, find or create the local user.
     * Returns the local user's UUID (NOT the Keycloak sub).
     */
    @Transactional
    public UUID resolveUserId(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);

        if (existing.isPresent()) {
            return syncAndReturn(existing.get(), keycloakId, email, firstName, lastName);
        }

        if (email != null && !email.isBlank()) {
            Optional<User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent()) {
                User user = existingByEmail.get();

                if (!canAdoptKeycloakIdentity(user, keycloakId)) {
                    throw new IllegalStateException(
                        "Existing user email is already linked to a different Keycloak identity");
                }

                log.info("Aligning seeded/local user {} with Keycloak subject {}", email, keycloakId);
                return syncAndReturn(user, keycloakId, email, firstName, lastName);
            }
        }

        // Create new user (JIT provisioning)
        User newUser = User.builder()
            .keycloakId(keycloakId)
            .email(email != null ? email : keycloakId + "@unknown")
            .firstName(firstName != null ? firstName : "Unknown")
            .lastName(lastName != null ? lastName : "User")
            .localePreference("fr")
            .isActive(true)
            .lastLogin(Instant.now())
            .build();

        User saved = userRepository.save(newUser);
        log.info("JIT provisioned new user: {} (keycloakId: {})", saved.getEmail(), keycloakId);
        return saved.getId();
    }

    private UUID syncAndReturn(
            User user,
            String keycloakId,
            String email,
            String firstName,
            String lastName) {
        user.setLastLogin(Instant.now());

        boolean changed = false;
        if (keycloakId != null && !keycloakId.equals(user.getKeycloakId())) {
            user.setKeycloakId(keycloakId);
            changed = true;
        }
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }
        if (firstName != null && !firstName.equals(user.getFirstName())) {
            user.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.equals(user.getLastName())) {
            user.setLastName(lastName);
            changed = true;
        }

        if (changed) {
            log.debug("Synced profile for user: {}", user.getEmail());
        }

        userRepository.save(user);
        return user.getId();
    }

    private boolean canAdoptKeycloakIdentity(User user, String keycloakId) {
        String existingKeycloakId = user.getKeycloakId();
        return existingKeycloakId == null
            || existingKeycloakId.isBlank()
            || existingKeycloakId.equals(keycloakId)
            || existingKeycloakId.startsWith(SEEDED_KEYCLOAK_PLACEHOLDER_PREFIX);
    }
}
