package com.hris.auth.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.LocalAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates user accounts with access profiles. Fully local since the owned-auth
 * migration: user + profiles + activation token commit in ONE transaction (the
 * old Keycloak create-then-compensate flow is gone), and the activation email
 * goes out after commit, best-effort.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final UserRepository userRepository;
    private final AccessProfileRepository accessProfileRepository;
    private final UserAccessAssignmentService userAccessAssignmentService;
    private final LocalAccountService localAccountService;
    private final AuditLogService auditLogService;

    @Transactional
    public User provision(AccountProvisioningRequest request, UUID actorId) {
        if (request.profileIds() == null || request.profileIds().isEmpty()) {
            throw new IllegalArgumentException("At least one access profile must be assigned");
        }

        String normalizedEmail = normalizeEmail(request.email());
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("User email must be unique");
        }

        List<AccessProfile> profiles = accessProfileRepository.findByIdIn(request.profileIds());
        if (profiles.size() != request.profileIds().size()) {
            throw new EntityNotFoundException("Access profile not found");
        }
        if (profiles.stream().anyMatch(profile -> !profile.isActive())) {
            throw new IllegalStateException("Only active access profiles can be assigned");
        }

        User saved = Objects.requireNonNull(userRepository.save(User.builder()
            .email(normalizedEmail)
            .firstName(firstName)
            .lastName(lastName)
            .localePreference("fr")
            .isActive(true)
            .isSeed(true) // not yet activated; flipped on first password set
            .build()), "User provisioning failed: persistence returned null");

        for (AccessProfile profile : profiles) {
            userAccessAssignmentService.assignProfile(saved.getId(), profile.getId(), actorId);
        }

        localAccountService.initiateActivation(saved);

        auditLogService.log(actorId, AuditAction.CREATE, "user", saved.getId(), null, saved);
        return saved;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
