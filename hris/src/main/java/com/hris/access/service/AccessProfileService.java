package com.hris.access.service;

import com.hris.access.dto.AccessProfileCreateDto;
import com.hris.access.dto.AccessProfileResponseDto;
import com.hris.access.dto.AccessProfileUpdateDto;
import com.hris.access.dto.MenuItemResponseDto;
import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.MenuItem;
import com.hris.access.entity.ProfileMenuAccess;
import com.hris.access.entity.ProfilePermission;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.MenuItemRepository;
import com.hris.access.repository.ProfileMenuAccessRepository;
import com.hris.access.repository.ProfilePermissionRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.repository.PermissionRepository;
import com.hris.common.PageResponse;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessProfileService {

    private static final Map<String, List<String>> MENU_REQUIRED_PERMISSIONS = Map.of(
        "menu.administration.employees", List.of("EMPLOYEE_READ", "EMPLOYEE_MANAGE", "DEPARTMENT_READ"),
        "menu.administration.teams", List.of("TEAM_READ", "TEAM_MANAGE", "DEPARTMENT_READ", "DEPARTMENT_MANAGE", "EMPLOYEE_READ", "EMPLOYEE_MANAGE", "PROJECT_PORTFOLIO_MANAGE", "PROJECT_ASSIGNMENT_MANAGE"),
        "menu.administration.teamHierarchy", List.of("TEAM_READ"),
        "menu.settings.projects", List.of("PROJECT_PORTFOLIO_MANAGE", "PROJECT_ASSIGNMENT_MANAGE", "TEAM_MANAGE", "TEAM_READ", "DEPARTMENT_READ", "DEPARTMENT_MANAGE", "EMPLOYEE_READ", "EMPLOYEE_MANAGE")
    );

    private final AccessProfileRepository accessProfileRepository;
    private final PermissionRepository permissionRepository;
    private final MenuItemRepository menuItemRepository;
    private final ProfilePermissionRepository profilePermissionRepository;
    private final ProfileMenuAccessRepository profileMenuAccessRepository;
    private final UserProfileAssignmentRepository userProfileAssignmentRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<AccessProfileResponseDto> getAll(Pageable pageable) {
        return PageResponse.of(accessProfileRepository.findAllByOrderByDisplayKeyAsc(pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public AccessProfileResponseDto getById(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public AccessProfileResponseDto create(AccessProfileCreateDto dto, UUID actorId) {
        validateCode(dto.code(), null);
        AccessProfile profile = AccessProfile.builder()
            .code(normalizeCode(dto.code()))
            .displayKey(dto.displayKey().trim())
            .descriptionKey(dto.descriptionKey() == null || dto.descriptionKey().isBlank() ? null : dto.descriptionKey().trim())
            .isSystemProfile(false)
            .isActive(dto.active() == null || dto.active())
            .build();
        AccessProfile saved = accessProfileRepository.save(profile);
        auditLogService.log(actorId, AuditAction.CREATE, "access_profile", saved.getId(), null, saved);
        return toDto(saved);
    }

    @Transactional
    public AccessProfileResponseDto update(UUID id, AccessProfileUpdateDto dto, UUID actorId) {
        AccessProfile existing = getEntity(id);
        validateCode(dto.code() == null ? existing.getCode() : dto.code(), id);

        Map<String, Object> previous = snapshot(existing);
        if (dto.code() != null) {
            existing.setCode(normalizeCode(dto.code()));
        }
        if (dto.displayKey() != null) {
            existing.setDisplayKey(dto.displayKey().trim());
        }
        if (dto.descriptionKey() != null) {
            existing.setDescriptionKey(dto.descriptionKey().isBlank() ? null : dto.descriptionKey().trim());
        }
        if (dto.active() != null) {
            existing.setActive(dto.active());
        }
        AccessProfile saved = accessProfileRepository.save(existing);
        auditLogService.log(actorId, AuditAction.UPDATE, "access_profile", saved.getId(), previous, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        AccessProfile existing = getEntity(id);
        if (existing.isSystemProfile()) {
            throw new IllegalStateException("System access profiles cannot be deactivated");
        }
        existing.setActive(false);
        accessProfileRepository.save(existing);
        auditLogService.log(actorId, AuditAction.DELETE, "access_profile", existing.getId(), null, snapshot(existing));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getPermissions(UUID profileId) {
        List<UUID> permissionIds = profilePermissionRepository.findByProfileId(profileId).stream()
            .map(ProfilePermission::getPermissionId)
            .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findByIdInAndIsActiveTrue(permissionIds).stream()
            .map(this::toPermissionDto)
            .toList();
    }

    @Transactional
    public List<PermissionResponseDto> assignPermissions(UUID profileId, List<UUID> permissionIds, UUID actorId) {
        getEntity(profileId);
        profilePermissionRepository.deleteByProfileId(profileId);
        Instant grantedAt = Instant.now();
        for (UUID permissionId : permissionIds) {
            permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found"));
            profilePermissionRepository.save(ProfilePermission.builder()
                .profileId(profileId)
                .permissionId(permissionId)
                .grantedAt(grantedAt)
                .grantedById(actorId)
                .build());
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "access_profile_permission", profileId, null, permissionIds);
        return getPermissions(profileId);
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponseDto> getMenus(UUID profileId) {
        List<UUID> menuIds = profileMenuAccessRepository.findByProfileId(profileId).stream()
            .map(ProfileMenuAccess::getMenuItemId)
            .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuItemRepository.findByIdIn(menuIds).stream()
            .sorted(java.util.Comparator.comparing(MenuItem::getSectionCode).thenComparingInt(MenuItem::getDisplayOrder))
            .map(this::toMenuDto)
            .toList();
    }

    @Transactional
    public List<MenuItemResponseDto> assignMenus(UUID profileId, List<UUID> menuItemIds, UUID actorId) {
        getEntity(profileId);
        profileMenuAccessRepository.deleteByProfileId(profileId);
        Instant grantedAt = Instant.now();
        List<MenuItem> selectedMenuItems = new ArrayList<>();
        for (UUID menuItemId : menuItemIds) {
            MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new EntityNotFoundException("Menu item not found"));
            selectedMenuItems.add(menuItem);
            profileMenuAccessRepository.save(ProfileMenuAccess.builder()
                .profileId(profileId)
                .menuItemId(menuItemId)
                .grantedAt(grantedAt)
                .grantedById(actorId)
                .build());
        }
        grantPermissionsRequiredByMenus(profileId, selectedMenuItems, actorId, grantedAt);
        auditLogService.log(actorId, AuditAction.UPDATE, "access_profile_menu", profileId, null, menuItemIds);
        return getMenus(profileId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponseDto> getAllMenus(Pageable pageable) {
        return PageResponse.of(menuItemRepository.findAllByOrderBySectionCodeAscDisplayOrderAsc(pageable).map(this::toMenuDto));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponseDto> getAllMenus() {
        return menuItemRepository.findAllByIsActiveTrueOrderBySectionCodeAscDisplayOrderAsc().stream()
            .map(this::toMenuDto)
            .toList();
    }

    private AccessProfile getEntity(UUID id) {
        return accessProfileRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Access profile not found"));
    }

    private void validateCode(String code, UUID currentId) {
        String normalizedCode = normalizeCode(code);
        boolean exists = currentId == null
            ? accessProfileRepository.existsByCodeIgnoreCase(normalizedCode)
            : accessProfileRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, currentId);
        if (exists) {
            throw new IllegalStateException("Access profile code must be unique");
        }
    }

    private AccessProfileResponseDto toDto(AccessProfile profile) {
        return new AccessProfileResponseDto(
            profile.getId(),
            profile.getCode(),
            profile.getDisplayKey(),
            profile.getDescriptionKey(),
            profile.isSystemProfile(),
            profile.isActive(),
            userProfileAssignmentRepository.countByProfileIdAndIsActiveTrue(profile.getId())
        );
    }

    private PermissionResponseDto toPermissionDto(Permission permission) {
        return new PermissionResponseDto(
            permission.getId(),
            permission.getName(),
            permission.getResource(),
            permission.getAction(),
            permission.getDescription(),
            permission.isActive()
        );
    }

    private MenuItemResponseDto toMenuDto(MenuItem item) {
        return new MenuItemResponseDto(
            item.getId(),
            item.getCode(),
            item.getTranslationKey(),
            item.getSectionCode(),
            item.getRoute(),
            item.getIcon(),
            item.getDisplayOrder(),
            item.isActive()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void grantPermissionsRequiredByMenus(
            UUID profileId,
            List<MenuItem> selectedMenuItems,
            UUID actorId,
            Instant grantedAt) {
        Set<String> requiredPermissionNames = selectedMenuItems.stream()
            .flatMap(menuItem -> MENU_REQUIRED_PERMISSIONS.getOrDefault(menuItem.getCode(), List.of()).stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requiredPermissionNames.isEmpty()) {
            return;
        }

        Set<UUID> existingPermissionIds = profilePermissionRepository.findByProfileId(profileId).stream()
            .map(ProfilePermission::getPermissionId)
            .collect(java.util.stream.Collectors.toSet());

        for (String permissionName : requiredPermissionNames) {
            Permission permission = permissionRepository.findByNameIgnoreCase(permissionName)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found"));
            if (existingPermissionIds.add(permission.getId())) {
                profilePermissionRepository.save(ProfilePermission.builder()
                    .profileId(profileId)
                    .permissionId(permission.getId())
                    .grantedAt(grantedAt)
                    .grantedById(actorId)
                    .build());
            }
        }
    }

    private Map<String, Object> snapshot(AccessProfile profile) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", profile.getCode());
        state.put("displayKey", profile.getDisplayKey());
        state.put("descriptionKey", profile.getDescriptionKey());
        state.put("isActive", profile.isActive());
        return state;
    }
}
