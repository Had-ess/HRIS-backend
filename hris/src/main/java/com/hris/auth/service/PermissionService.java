package com.hris.auth.service;

import com.hris.auth.dto.PermissionCreateDto;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.dto.PermissionUpdateDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.PermissionDeletionNotAllowedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String DEFAULT_SCOPE = "GLOBAL";

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getAll() {
        return permissionRepository.findAllByOrderByResourceAscActionAsc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponseDto getById(UUID id) {
        return toDto(getEntityById(id));
    }

    @Transactional
    public PermissionResponseDto create(PermissionCreateDto dto) {
        validateUniqueness(dto.name(), dto.resource(), dto.action(), null);

        Permission permission = Permission.builder()
            .name(normalizeName(dto.name()))
            .resource(normalizeValue(dto.resource()))
            .action(normalizeValue(dto.action()))
            .scope(DEFAULT_SCOPE)
            .description(normalizeDescription(dto.description()))
            .isActive(dto.active() == null || dto.active())
            .build();

        return toDto(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponseDto update(UUID id, PermissionUpdateDto dto) {
        Permission permission = getEntityById(id);

        String nextName = dto.name() != null ? dto.name() : permission.getName();
        String nextResource = dto.resource() != null ? dto.resource() : permission.getResource();
        String nextAction = dto.action() != null ? dto.action() : permission.getAction();

        validateUniqueness(nextName, nextResource, nextAction, id);

        if (dto.name() != null) {
            permission.setName(normalizeName(dto.name()));
        }
        if (dto.resource() != null) {
            permission.setResource(normalizeValue(dto.resource()));
        }
        if (dto.action() != null) {
            permission.setAction(normalizeValue(dto.action()));
        }
        if (dto.description() != null) {
            permission.setDescription(normalizeDescription(dto.description()));
        }
        if (dto.active() != null) {
            permission.setActive(dto.active());
        }

        return toDto(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(UUID id) {
        Permission permission = getEntityById(id);
        if (rolePermissionRepository.existsByPermissionId(id)) {
            throw new PermissionDeletionNotAllowedException(
                "Permission cannot be deleted because it is assigned to one or more roles");
        }
        permissionRepository.delete(permission);
    }

    @Transactional(readOnly = true)
    public Permission getEntityById(UUID id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Permission not found"));
    }

    private void validateUniqueness(String name, String resource, String action, UUID permissionId) {
        String normalizedName = normalizeName(name);
        String normalizedResource = normalizeValue(resource);
        String normalizedAction = normalizeValue(action);

        boolean duplicateName = permissionId == null
            ? permissionRepository.existsByNameIgnoreCase(normalizedName)
            : permissionRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, permissionId);
        if (duplicateName) {
            throw new IllegalStateException("Permission name must be unique");
        }

        boolean duplicateResourceAction = permissionId == null
            ? permissionRepository.existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCase(
                normalizedResource, normalizedAction, DEFAULT_SCOPE)
            : permissionRepository.existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCaseAndIdNot(
                normalizedResource, normalizedAction, DEFAULT_SCOPE, permissionId);
        if (duplicateResourceAction) {
            throw new IllegalStateException("A permission already exists for this resource and action");
        }
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

    private String normalizeName(String value) {
        return normalizeValue(value);
    }

    private String normalizeValue(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
