package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.KeycloakAdminUserCreateRequest;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.KeycloakProvisioningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;

    @Transactional
    public User provision(AccountProvisioningRequest request, UUID actorId) {
        if (request.roleIds() == null || request.roleIds().isEmpty()) {
            throw new IllegalArgumentException("At least one role must be assigned");
        }

        String normalizedEmail = normalizeEmail(request.email());
        String normalizedUsername = normalizeUsername(request.username());
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("User email must be unique");
        }

        List<Role> roles = roleRepository.findAllById(request.roleIds());
        if (roles.size() != request.roleIds().size()) {
            throw new EntityNotFoundException("Role not found");
        }
        if (roles.stream().anyMatch(role -> !role.isActive())) {
            throw new IllegalStateException("Only active roles can be assigned");
        }

        String keycloakUserId = keycloakAdminClient.createUser(new KeycloakAdminUserCreateRequest(
            normalizedUsername,
            normalizedEmail,
            firstName,
            lastName,
            request.password(),
            request.temporaryPassword(),
            roles.stream().map(Role::getCode).toList()
        ));

        try {
            User saved = userRepository.save(User.builder()
                .keycloakId(keycloakUserId)
                .email(normalizedEmail)
                .firstName(firstName)
                .lastName(lastName)
                .localePreference("fr")
                .isActive(true)
                .build());

            for (Role role : roles) {
                userRoleAssignmentService.assignRole(saved.getId(), role.getId(), actorId);
            }

            auditLogService.log(actorId, AuditAction.CREATE, "user", saved.getId(), null, saved);
            return saved;
        } catch (RuntimeException ex) {
            rollbackExternalAccount(keycloakUserId);
            throw ex;
        }
    }

    public void rollbackExternalAccount(String keycloakUserId) {
        try {
            keycloakAdminClient.deleteUser(keycloakUserId);
        } catch (KeycloakProvisioningException ex) {
            log.warn("Failed to rollback Keycloak user {} after onboarding failure", keycloakUserId, ex);
        } catch (RuntimeException ex) {
            log.warn("Failed to rollback Keycloak user {} after onboarding failure", keycloakUserId, ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
