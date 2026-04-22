package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.RoleAlreadyAssignedToUserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Role> getRoles(UUID userId) {
        ensureUserExists(userId);
        return userRoleRepository.findEffectiveByUserId(userId, Instant.now()).stream()
            .map(UserRole::getRole)
            .filter(role -> role != null)
            .toList();
    }

    @Transactional
    public List<Role> assignRole(UUID userId, UUID roleId, UUID actorId) {
        ensureUserExists(userId);
        ensureRoleExists(roleId);
        ensureUserExists(actorId);

        if (userRoleRepository.existsByUserIdAndRoleIdAndIsActiveTrue(userId, roleId)) {
            throw new RoleAlreadyAssignedToUserException("Role is already assigned to this user");
        }

        UserRole userRole = UserRole.builder()
            .userId(userId)
            .roleId(roleId)
            .isActive(true)
            .build();
        UserRole saved = userRoleRepository.save(userRole);
        auditLogService.log(actorId, AuditAction.CREATE, "user_role",
            saved.getId(), null, saved);

        return getRoles(userId);
    }

    @Transactional
    public void removeRole(UUID userId, UUID roleId, UUID actorId) {
        ensureUserExists(userId);
        ensureRoleExists(roleId);
        ensureUserExists(actorId);

        userRoleRepository.findByUserIdAndRoleIdAndIsActiveTrue(userId, roleId)
            .ifPresent(userRole -> {
                UserRole previous = UserRole.builder()
                    .id(userRole.getId())
                    .userId(userRole.getUserId())
                    .roleId(userRole.getRoleId())
                    .isActive(userRole.isActive())
                    .assignedAt(userRole.getAssignedAt())
                    .expiresAt(userRole.getExpiresAt())
                    .build();
                userRole.setActive(false);
                userRole.setExpiresAt(Instant.now());
                UserRole saved = userRoleRepository.save(userRole);
                auditLogService.log(actorId, AuditAction.DELETE, "user_role",
                    saved.getId(), previous, saved);
            });
    }

    private void ensureUserExists(UUID userId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private void ensureRoleExists(UUID roleId) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }
}
