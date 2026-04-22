package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.RoleCreateDto;
import com.hris.auth.dto.RoleResponseDto;
import com.hris.auth.dto.RoleUpdateDto;
import com.hris.auth.entity.Role;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidRoleHierarchyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RoleResponseDto> getAll() {
        List<Role> roles = roleRepository.findAllByOrderByNameAsc();
        return toDtos(roles);
    }

    @Transactional(readOnly = true)
    public RoleResponseDto getById(UUID id) {
        return toDto(getEntityById(id), roleRepository.findAllByOrderByNameAsc().stream()
            .collect(Collectors.toMap(Role::getId, Function.identity())));
    }

    @Transactional
    public RoleResponseDto create(RoleCreateDto dto, UUID actorId) {
        validateUniqueness(dto.code(), dto.name(), null);

        if (dto.parentId() != null) {
            validateNoRoleCycle(null, dto.parentId());
        }

        Role role = Role.builder()
            .code(normalizeCode(dto.code()))
            .name(normalizeName(dto.name()))
            .isActive(dto.active() == null || dto.active())
            .parentId(dto.parentId())
            .isSystemRole(false)
            .build();

        Role saved = roleRepository.save(role);
        auditLogService.log(actorId, AuditAction.CREATE, "role", saved.getId(), null, saved);
        return toDto(saved, loadRoleMap(saved.getParentId()));
    }

    @Transactional
    public RoleResponseDto update(UUID id, RoleUpdateDto dto, UUID actorId) {
        Role existing = getEntityById(id);

        if (existing.isSystemRole()) {
            throw new IllegalStateException("System roles cannot be modified");
        }

        String nextCode = dto.code() != null ? dto.code() : existing.getCode();
        String nextName = dto.name() != null ? dto.name() : existing.getName();
        UUID proposedParentId = dto.parentId() != null ? dto.parentId() : null;

        validateUniqueness(nextCode, nextName, id);

        if (dto.parentId() != null) {
            validateNoRoleCycle(id, proposedParentId);
        }

        Role previous = Role.builder()
            .id(existing.getId())
            .code(existing.getCode())
            .name(existing.getName())
            .isSystemRole(existing.isSystemRole())
            .isActive(existing.isActive())
            .parentId(existing.getParentId())
            .build();

        if (dto.code() != null) {
            existing.setCode(normalizeCode(dto.code()));
        }
        if (dto.name() != null) {
            existing.setName(normalizeName(dto.name()));
        }
        if (dto.active() != null) {
            existing.setActive(dto.active());
        }
        if (dto.parentId() != null || existing.getParentId() != null) {
            existing.setParentId(dto.parentId());
        }

        Role saved = roleRepository.save(existing);
        auditLogService.log(actorId, AuditAction.UPDATE, "role", saved.getId(), previous, saved);
        return toDto(saved, loadRoleMap(saved.getParentId()));
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        Role existing = getEntityById(id);
        if (existing.isSystemRole()) {
            throw new IllegalStateException("Cannot deactivate a system role");
        }
        if (userRoleRepository.existsEffectiveByRoleId(id, Instant.now())) {
            throw new IllegalStateException("Role cannot be deactivated because it is still assigned to users");
        }

        Role previous = Role.builder()
            .id(existing.getId())
            .code(existing.getCode())
            .name(existing.getName())
            .isSystemRole(existing.isSystemRole())
            .isActive(existing.isActive())
            .parentId(existing.getParentId())
            .build();

        existing.setActive(false);
        Role saved = roleRepository.save(existing);
        auditLogService.log(actorId, AuditAction.DELETE, "role", saved.getId(), previous, saved);
    }

    @Transactional(readOnly = true)
    public Role getEntityById(UUID id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    void validateNoRoleCycle(UUID roleId, UUID proposedParentId) {
        if (proposedParentId == null) {
            return;
        }
        if (roleId != null && roleId.equals(proposedParentId)) {
            throw new InvalidRoleHierarchyException("Role cannot be its own parent");
        }

        UUID currentRoleId = proposedParentId;
        while (currentRoleId != null) {
            Role currentRole = roleRepository.findById(currentRoleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));

            if (roleId != null && roleId.equals(currentRole.getId())) {
                throw new InvalidRoleHierarchyException("Role hierarchy cycle detected");
            }

            currentRoleId = currentRole.getParentId();
        }
    }

    private void validateUniqueness(String code, String name, UUID roleId) {
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);

        boolean duplicateCode = roleId == null
            ? roleRepository.existsByCodeIgnoreCase(normalizedCode)
            : roleRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, roleId);
        if (duplicateCode) {
            throw new IllegalStateException("Role code must be unique");
        }

        boolean duplicateName = roleId == null
            ? roleRepository.existsByNameIgnoreCase(normalizedName)
            : roleRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, roleId);
        if (duplicateName) {
            throw new IllegalStateException("Role name must be unique");
        }
    }

    private List<RoleResponseDto> toDtos(List<Role> roles) {
        Map<UUID, Role> roleMap = roles.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return roles.stream()
            .map(role -> toDto(role, roleMap))
            .toList();
    }

    private RoleResponseDto toDto(Role role, Map<UUID, Role> roleMap) {
        Role parent = role.getParentId() != null ? roleMap.get(role.getParentId()) : null;
        return new RoleResponseDto(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.isSystemRole(),
            role.isActive(),
            role.getParentId(),
            parent != null ? parent.getName() : null,
            userRoleRepository.countEffectiveByRoleId(role.getId(), Instant.now())
        );
    }

    private Map<UUID, Role> loadRoleMap(UUID relatedParentId) {
        if (relatedParentId == null) {
            return roleRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        }
        return roleRepository.findAllByOrderByNameAsc().stream()
            .collect(Collectors.toMap(Role::getId, Function.identity()));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
