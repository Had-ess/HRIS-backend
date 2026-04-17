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
            User user = existing.get();
            // Update last login
            user.setLastLogin(Instant.now());
            // Sync profile if changed
            boolean changed = false;
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
                log.debug("Synced profile for user: {}", email);
            }
            userRepository.save(user);
            return user.getId();
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
}
