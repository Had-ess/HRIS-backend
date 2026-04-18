package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.entity.User;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.PermissionAlreadyAssignedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getPermissions(UUID roleId) {
        ensureRoleExists(roleId);
        return rolePermissionRepository.findByRoleId(roleId).stream()
            .map(RolePermission::getPermission)
            .filter(permission -> permission != null)
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public List<PermissionResponseDto> assignPermissions(UUID roleId, List<UUID> permissionIds, UUID grantedById) {
        ensureRoleExists(roleId);
        ensureActorExists(grantedById);

        Set<UUID> distinctPermissionIds = new LinkedHashSet<>(permissionIds);
        List<Permission> permissions = permissionRepository.findAllById(distinctPermissionIds);
        if (permissions.size() != distinctPermissionIds.size()) {
            throw new EntityNotFoundException("Permission not found");
        }

        for (UUID permissionId : distinctPermissionIds) {
            if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
                throw new PermissionAlreadyAssignedException("Permission is already assigned to this role");
            }
        }

        List<RolePermission> links = distinctPermissionIds.stream()
            .map(permissionId -> RolePermission.builder()
                .roleId(roleId)
                .permissionId(permissionId)
                .grantedById(grantedById)
                .build())
            .toList();

        List<RolePermission> savedLinks = rolePermissionRepository.saveAll(links);
        savedLinks.forEach(link -> auditLogService.log(
            grantedById,
            AuditAction.CREATE,
            "role_permission",
            link.getId(),
            null,
            link
        ));
        return getPermissions(roleId);
    }

    @Transactional
    public void removePermission(UUID roleId, UUID permissionId, UUID actorId) {
        ensureRoleExists(roleId);
        ensurePermissionExists(permissionId);
        ensureActorExists(actorId);
        rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
            .ifPresent(rolePermission -> {
                rolePermissionRepository.delete(rolePermission);
                auditLogService.log(actorId, AuditAction.DELETE, "role_permission",
                    rolePermission.getId(), rolePermission, null);
            });
    }

    private void ensureRoleExists(UUID roleId) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    private void ensurePermissionExists(UUID permissionId) {
        permissionRepository.findById(permissionId)
            .orElseThrow(() -> new EntityNotFoundException("Permission not found"));
    }

    private void ensureActorExists(UUID userId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private PermissionResponseDto toDto(Permission permission) {
        return new PermissionResponseDto(
            permission.getId(),
            permission.getName(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription(),
            permission.isActive()
        );
    }
}
