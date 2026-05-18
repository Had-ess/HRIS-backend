package com.hris.access.service;

import com.hris.access.dto.AccessMeResponseDto;
import com.hris.access.dto.AccessPermissionDto;
import com.hris.access.dto.NavigationItemDto;
import com.hris.access.dto.NavigationSectionDto;
import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.MenuItem;
import com.hris.access.entity.ProfileMenuAccess;
import com.hris.access.entity.ProfilePermission;
import com.hris.access.entity.UserProfileAssignment;
import com.hris.access.repository.MenuItemRepository;
import com.hris.access.repository.ProfileMenuAccessRepository;
import com.hris.access.repository.ProfilePermissionRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.auth.entity.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessResolutionService {

    private static final Map<String, String> SECTION_TRANSLATION_KEYS = Map.of(
        "WORKSPACE", "menu.section.workspace",
        "ADMINISTRATION", "menu.section.administration",
        "SETTINGS", "menu.section.settings"
    );

    private static final Map<String, Integer> SECTION_ORDER = Map.of(
        "WORKSPACE", 1,
        "ADMINISTRATION", 2,
        "SETTINGS", 3
    );

    private final UserProfileAssignmentRepository userProfileAssignmentRepository;
    private final ProfilePermissionRepository profilePermissionRepository;
    private final ProfileMenuAccessRepository profileMenuAccessRepository;
    private final MenuItemRepository menuItemRepository;

    @Transactional(readOnly = true)
    public List<UserProfileAssignment> getEffectiveAssignments(UUID userId) {
        return userProfileAssignmentRepository.findEffectiveByUserId(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<AccessProfile> getEffectiveProfiles(UUID userId) {
        return getEffectiveAssignments(userId).stream()
            .map(UserProfileAssignment::getProfile)
            .filter(profile -> profile != null && profile.isActive())
            .sorted(Comparator.comparing(AccessProfile::getCode))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Permission> getEffectivePermissions(UUID userId) {
        List<UUID> profileIds = getEffectiveProfiles(userId).stream()
            .map(AccessProfile::getId)
            .toList();
        if (profileIds.isEmpty()) {
            return List.of();
        }

        return profilePermissionRepository.findByProfileIdIn(profileIds).stream()
            .map(ProfilePermission::getPermission)
            .filter(permission -> permission != null && permission.isActive())
            .sorted(Comparator.comparing(Permission::getName))
            .distinct()
            .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissionNames(UUID userId) {
        return getEffectivePermissions(userId).stream()
            .map(Permission::getName)
            .map(this::normalize)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<String> getEffectiveProfileCodes(UUID userId) {
        return getEffectiveProfiles(userId).stream()
            .map(AccessProfile::getCode)
            .map(this::normalize)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String resource, String action) {
        String normalizedResource = normalize(resource);
        String normalizedAction = normalize(action);
        return getEffectivePermissions(userId).stream().anyMatch(permission ->
            normalize(permission.getResource()).equals(normalizedResource)
                && normalize(permission.getAction()).equals(normalizedAction)
        );
    }

    @Transactional(readOnly = true)
    public boolean hasPermissionName(UUID userId, String permissionName) {
        return getEffectivePermissionNames(userId).contains(normalize(permissionName));
    }

    @Transactional(readOnly = true)
    public AccessMeResponseDto resolveAccess(UUID userId) {
        List<AccessProfile> profiles = getEffectiveProfiles(userId);
        List<Permission> permissions = getEffectivePermissions(userId);

        List<String> profileCodes = profiles.stream()
            .map(AccessProfile::getCode)
            .sorted()
            .toList();

        List<AccessPermissionDto> permissionDtos = permissions.stream()
            .map(permission -> new AccessPermissionDto(
                permission.getName(),
                permission.getResource(),
                permission.getAction(),
                permission.getScope()
            ))
            .toList();

        List<String> scopes = permissions.stream()
            .map(Permission::getScope)
            .filter(scope -> scope != null && !scope.isBlank())
            .map(this::normalize)
            .distinct()
            .sorted()
            .toList();

        return new AccessMeResponseDto(profileCodes, permissionDtos, scopes);
    }

    @Transactional(readOnly = true)
    public List<NavigationSectionDto> resolveNavigation(UUID userId) {
        List<UUID> profileIds = getEffectiveProfiles(userId).stream()
            .map(AccessProfile::getId)
            .toList();
        if (profileIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> menuItemIds = profileMenuAccessRepository.findByProfileIdIn(profileIds).stream()
            .map(ProfileMenuAccess::getMenuItemId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (menuItemIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, MenuItem> itemsById = menuItemRepository.findByIdIn(menuItemIds).stream()
            .filter(MenuItem::isActive)
            .collect(java.util.stream.Collectors.toMap(MenuItem::getId, item -> item));

        Map<String, List<NavigationItemDto>> sections = new LinkedHashMap<>();
        profileMenuAccessRepository.findByProfileIdIn(profileIds).stream()
            .map(ProfileMenuAccess::getMenuItemId)
            .distinct()
            .map(itemsById::get)
            .filter(item -> item != null && item.isActive())
            .sorted(Comparator.comparing(MenuItem::getSectionCode).thenComparingInt(MenuItem::getDisplayOrder))
            .forEach(item -> sections.computeIfAbsent(item.getSectionCode(), ignored -> new ArrayList<>()).add(
                new NavigationItemDto(
                    item.getCode(),
                    item.getTranslationKey(),
                    item.getSectionCode(),
                    item.getRoute(),
                    item.getIcon(),
                    item.getDisplayOrder()
                )
            ));

        return sections.entrySet().stream()
            .map(entry -> new NavigationSectionDto(
                entry.getKey(),
                SECTION_TRANSLATION_KEYS.getOrDefault(entry.getKey(), "menu.section." + entry.getKey().toLowerCase(Locale.ROOT)),
                entry.getValue()
            ))
            .sorted(Comparator.comparingInt(s -> SECTION_ORDER.getOrDefault(s.code(), 99)))
            .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
