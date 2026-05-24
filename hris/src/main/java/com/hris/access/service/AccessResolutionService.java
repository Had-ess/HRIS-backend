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
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.entity.Permission;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessResolutionService {

    private static final Map<String, String> SECTION_TRANSLATION_KEYS = Map.of(
        "WORKSPACE", "menu.section.workspace",
        "TIME_OFF", "menu.section.timeOff",
        "REQUESTS", "menu.section.requests",
        "PEOPLE", "menu.section.people",
        "INSIGHTS", "menu.section.insights",
        "CONFIGURATION", "menu.section.configuration",
        "ADMINISTRATION", "menu.section.administration",
        "SETTINGS", "menu.section.settings"
    );

    private static final Map<String, Integer> SECTION_ORDER = Map.of(
        "WORKSPACE", 1,
        "TIME_OFF", 2,
        "REQUESTS", 3,
        "PEOPLE", 4,
        "INSIGHTS", 5,
        "CONFIGURATION", 6,
        "ADMINISTRATION", 7,
        "SETTINGS", 8
    );

    private final UserProfileAssignmentRepository userProfileAssignmentRepository;
    private final ProfilePermissionRepository profilePermissionRepository;
    private final ProfileMenuAccessRepository profileMenuAccessRepository;
    private final MenuItemRepository menuItemRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;

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
    public boolean hasAnyManualPermissionName(UUID userId, String... permissionNames) {
        List<UserProfileAssignment> assignments = getEffectiveAssignments(userId);
        if (assignments.isEmpty()) {
            return false;
        }

        Set<UUID> profileIdsGrantingPermission = profileIdsGrantingAnyPermission(assignments, List.of(permissionNames));
        if (profileIdsGrantingPermission.isEmpty()) {
            return false;
        }

        return assignments.stream()
            .anyMatch(assignment -> "MANUAL".equalsIgnoreCase(assignment.getAssignmentSource())
                && profileIdsGrantingPermission.contains(assignment.getProfileId()));
    }

    /**
     * Resolves department scope from the assignments that actually grant the supplied permissions.
     * A manual assignment is global; system assignments contribute their department sourceRefId.
     */
    @Transactional(readOnly = true)
    public ScopeResolution resolvePermissionDepartmentScope(UUID userId, String... permissionNames) {
        List<UserProfileAssignment> assignments = getEffectiveAssignments(userId);
        if (assignments.isEmpty()) {
            return ScopeResolution.self();
        }

        Set<UUID> profileIdsGrantingPermission = profileIdsGrantingAnyPermission(assignments, List.of(permissionNames));
        if (profileIdsGrantingPermission.isEmpty()) {
            return ScopeResolution.self();
        }

        boolean hasManualGrant = assignments.stream()
            .anyMatch(assignment -> "MANUAL".equalsIgnoreCase(assignment.getAssignmentSource())
                && profileIdsGrantingPermission.contains(assignment.getProfileId()));
        if (hasManualGrant) {
            return ScopeResolution.global();
        }

        List<UUID> departmentIds = assignments.stream()
            .filter(assignment -> "SYSTEM".equalsIgnoreCase(assignment.getAssignmentSource()))
            .filter(assignment -> profileIdsGrantingPermission.contains(assignment.getProfileId()))
            .map(UserProfileAssignment::getSourceRefId)
            .filter(java.util.Objects::nonNull)
            .flatMap(scopeRefId -> resolveDepartmentIdsFromScopeRef(scopeRefId).stream())
            .distinct()
            .toList();

        return departmentIds.isEmpty() ? ScopeResolution.self() : ScopeResolution.department(departmentIds);
    }

    /**
     * Returns the department ids derived from active SYSTEM assignments only.
     * Ignores MANUAL grants entirely — useful when a manual profile (e.g. MANAGER_INBOX)
     * coexists with a SYSTEM scope (e.g. DEPT_APPROVER_PROFILE) and the system scope should win.
     */
    @Transactional(readOnly = true)
    public List<UUID> resolveSystemSourcedDepartmentIds(UUID userId) {
        List<UserProfileAssignment> assignments = getEffectiveAssignments(userId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
            .filter(assignment -> "SYSTEM".equalsIgnoreCase(assignment.getAssignmentSource()))
            .map(UserProfileAssignment::getSourceRefId)
            .filter(java.util.Objects::nonNull)
            .flatMap(scopeRefId -> resolveDepartmentIdsFromScopeRef(scopeRefId).stream())
            .distinct()
            .toList();
    }

    /**
     * Returns the department ids attached to active SYSTEM assignments.
     *
     * <p>Manual grants are intentionally treated as unrestricted, so any manual assignment turns
     * the current user into a global viewer for the access bootstrap payload.
     */
    @Transactional(readOnly = true)
    public List<UUID> resolveScopedDepartmentIds(UUID userId) {
        List<UserProfileAssignment> assignments = getEffectiveAssignments(userId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        boolean hasManualGrant = assignments.stream()
            .anyMatch(assignment -> "MANUAL".equalsIgnoreCase(assignment.getAssignmentSource()));
        if (hasManualGrant) {
            return List.of();
        }

        return assignments.stream()
            .filter(assignment -> "SYSTEM".equalsIgnoreCase(assignment.getAssignmentSource()))
            .map(UserProfileAssignment::getSourceRefId)
            .filter(java.util.Objects::nonNull)
            .flatMap(scopeRefId -> resolveDepartmentIdsFromScopeRef(scopeRefId).stream())
            .distinct()
            .toList();
    }

    /**
     * Returns whether the user holds the given permission within the supplied scope entity.
     *
     * <p>Resolution honors auto-granted profile assignments:
     * <ul>
     *   <li>MANUAL assignments are always unrestricted (system/HR admins granted by hand).</li>
     *   <li>SYSTEM assignments match only when {@code source_ref_id == scopeEntityId}.</li>
     * </ul>
     *
     * <p>If {@code scopeEntityId} is {@code null}, only MANUAL assignments can grant the permission.
     */
    @Transactional(readOnly = true)
    public boolean hasPermissionInScope(UUID userId, String permissionName, UUID scopeEntityId) {
        String normalized = normalize(permissionName);
        List<UserProfileAssignment> assignments = getEffectiveAssignments(userId);
        if (assignments.isEmpty()) {
            return false;
        }

        Set<UUID> profileIdsGrantingPermission = profileIdsGrantingAnyPermission(assignments, List.of(normalized));

        if (profileIdsGrantingPermission.isEmpty()) {
            return false;
        }

        for (UserProfileAssignment assignment : assignments) {
            if (!profileIdsGrantingPermission.contains(assignment.getProfileId())) {
                continue;
            }
            if ("MANUAL".equalsIgnoreCase(assignment.getAssignmentSource())) {
                return true;
            }
            if ("SYSTEM".equalsIgnoreCase(assignment.getAssignmentSource())
                && scopeEntityId != null
                && scopeEntityId.equals(assignment.getSourceRefId())) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> profileIdsGrantingAnyPermission(
            List<UserProfileAssignment> assignments,
            Collection<String> permissionNames) {
        Set<String> normalizedNames = permissionNames.stream()
            .map(this::normalize)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalizedNames.isEmpty()) {
            return Set.of();
        }

        List<UUID> candidateProfileIds = assignments.stream()
            .map(UserProfileAssignment::getProfileId)
            .distinct()
            .toList();
        if (candidateProfileIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> profileIdsGrantingPermission = new LinkedHashSet<>();
        profilePermissionRepository.findByProfileIdIn(candidateProfileIds).stream()
            .filter(profilePermission -> profilePermission.getPermission() != null
                && profilePermission.getPermission().isActive()
                && normalizedNames.contains(normalize(profilePermission.getPermission().getName())))
            .forEach(profilePermission -> profileIdsGrantingPermission.add(profilePermission.getProfileId()));
        return profileIdsGrantingPermission;
    }

    private List<UUID> resolveDepartmentIdsFromScopeRef(UUID scopeRefId) {
        if (scopeRefId == null) {
            return List.of();
        }

        Set<UUID> departmentIds = new LinkedHashSet<>();
        departmentRepository.findById(scopeRefId)
            .filter(department -> department.isActive())
            .ifPresent(department -> departmentIds.add(department.getId()));

        teamRepository.findById(scopeRefId)
            .filter(team -> team.isActive())
            .ifPresent(team -> departmentIds.add(team.getDepartmentId()));

        teamHierarchyRelationRepository.findById(scopeRefId)
            .flatMap(relation -> teamRepository.findById(relation.getTeamId()))
            .filter(team -> team.isActive())
            .ifPresent(team -> departmentIds.add(team.getDepartmentId()));

        projectAssignmentRepository.findById(scopeRefId)
            .filter(assignment -> assignment.isActive())
            .filter(assignment -> !assignment.getStartDate().isAfter(LocalDate.now()))
            .filter(assignment -> assignment.getEndDate() == null || !assignment.getEndDate().isBefore(LocalDate.now()))
            .ifPresent(assignment -> projectDepartmentRepository.findByProjectId(assignment.getProjectId()).stream()
                .map(projectDepartment -> projectDepartment.getDepartmentId())
                .forEach(departmentIds::add));

        projectDepartmentRepository.findByProjectId(scopeRefId).stream()
            .map(projectDepartment -> projectDepartment.getDepartmentId())
            .forEach(departmentIds::add);

        return List.copyOf(departmentIds);
    }

    /**
     * Resolves the breadth at which a user can see approval-relevant data (leave requests,
     * approval analytics).
     *
     * <ul>
     *   <li>{@link ScopeType#GLOBAL} — no WHERE clause; user sees everything.</li>
     *   <li>{@link ScopeType#DEPARTMENT} — restrict to the returned department ids.</li>
     *   <li>{@link ScopeType#SELF} — restrict to the user's own records.</li>
     * </ul>
     *
     * <p>A MANUAL profile assignment that grants approval permissions is treated as GLOBAL
     * (manual grants are unrestricted by design). SYSTEM-scoped approval-capable assignments
     * contribute their {@code sourceRefId}; callers can translate that reference into the
     * appropriate data boundary.
     */
    @Transactional(readOnly = true)
    public ScopeResolution resolveApprovalScope(UUID userId) {
        return resolvePermissionDepartmentScope(userId,
            "APPROVAL_STEP_READ",
            "APPROVAL_STEP_DECIDE",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_APPROVE"
        );
    }

    @Transactional(readOnly = true)
    public List<Employee> findEmployeesWithScopedProfile(String profileCode, UUID scopeEntityId, UUID excludeUserId, Instant now) {
        return userProfileAssignmentRepository.findEmployeesWithScopedProfile(profileCode, scopeEntityId, excludeUserId, now);
    }

    public enum ScopeType {
        GLOBAL,
        DEPARTMENT,
        SELF
    }

    public record ScopeResolution(ScopeType type, List<UUID> departmentIds) {
        public ScopeResolution {
            departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        }

        public static ScopeResolution global() {
            return new ScopeResolution(ScopeType.GLOBAL, List.of());
        }

        public static ScopeResolution department(List<UUID> departmentIds) {
            return new ScopeResolution(ScopeType.DEPARTMENT, departmentIds);
        }

        public static ScopeResolution self() {
            return new ScopeResolution(ScopeType.SELF, List.of());
        }

        public boolean isGlobal() {
            return type == ScopeType.GLOBAL;
        }

        public boolean isDepartment() {
            return type == ScopeType.DEPARTMENT;
        }

        public boolean isSelf() {
            return type == ScopeType.SELF;
        }
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

        List<UUID> scopedDepartmentIds = resolveScopedDepartmentIds(userId);
        if (scopedDepartmentIds.isEmpty() && getEffectiveAssignments(userId).isEmpty()) {
            scopedDepartmentIds = List.of();
        }
        if (scopedDepartmentIds.isEmpty()) {
            boolean hasManualGrant = getEffectiveAssignments(userId).stream()
                .anyMatch(assignment -> "MANUAL".equalsIgnoreCase(assignment.getAssignmentSource()));
            scopedDepartmentIds = hasManualGrant ? null : List.of();
        }

        return new AccessMeResponseDto(profileCodes, permissionDtos, scopes, scopedDepartmentIds);
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
