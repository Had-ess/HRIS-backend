package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycloakSyncService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public User findOrCreateByKeycloakId(String keycloakId, String email,
                                          String firstName, String lastName) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseGet(() -> {
                User user = User.builder()
                    .keycloakId(keycloakId)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .localePreference("fr")
                    .isActive(true)
                    .build();
                User saved = userRepository.save(user);
                auditLogService.log(saved.getId(), AuditAction.CREATE,
                    "user", saved.getId(), null, saved);
                return saved;
            });
    }

    @Transactional
    public void updateLastLogin(UUID userId) {
        userRepository.updateLastLogin(userId, Instant.now());
    }
}
