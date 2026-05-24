package com.hris.access.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessResolutionServiceTest {

    @Mock
    private UserProfileAssignmentRepository userProfileAssignmentRepository;

    @Mock
    private ProfilePermissionRepository profilePermissionRepository;

    @Mock
    private ProfileMenuAccessRepository profileMenuAccessRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private AccessResolutionService accessResolutionService;

    @Test
    @DisplayName("resolves effective access and navigation from assigned profiles")
    void resolvesEffectiveAccessAndNavigation() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();

        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("HR_CONSOLE")
            .displayKey("profile.hrConsole")
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("ACCESS_PROFILE_READ")
            .resource("ACCESS_PROFILE")
            .action("READ")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        MenuItem menuItem = MenuItem.builder()
            .id(menuId)
            .code("menu.workspace.dashboard")
            .translationKey("menu.workspace.dashboard")
            .sectionCode("WORKSPACE")
            .route("/dashboard")
            .icon("home")
            .displayOrder(10)
            .isActive(true)
            .build();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of(UserProfileAssignment.builder()
                .profile(profile)
                .profileId(profileId)
                .assignmentSource("MANUAL")
                .build()));
        when(profilePermissionRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(ProfilePermission.builder().permission(permission).permissionId(permissionId).build()));
        when(profileMenuAccessRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(ProfileMenuAccess.builder().menuItemId(menuId).build()));
        when(menuItemRepository.findByIdIn(anyCollection())).thenReturn(List.of(menuItem));

        var access = accessResolutionService.resolveAccess(userId);
        var navigation = accessResolutionService.resolveNavigation(userId);

        assertThat(access.profileCodes()).containsExactly("HR_CONSOLE");
        assertThat(access.scopedDepartmentIds()).isEmpty();
        assertThat(access.permissions()).extracting(permissionDto -> permissionDto.name())
            .containsExactly("ACCESS_PROFILE_READ");
        assertThat(navigation).hasSize(1);
        assertThat(navigation.getFirst().items()).extracting(item -> item.code())
            .containsExactly("menu.workspace.dashboard");
    }

    @Test
    @DisplayName("access response exposes department scope for system-granted approver profiles")
    void resolveAccessExposesScopedDepartmentIds() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("DEPT_APPROVER_PROFILE")
            .displayKey("profile.deptApprover")
            .isActive(true)
            .build();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of(UserProfileAssignment.builder()
                .profile(profile)
                .profileId(profileId)
                .assignmentSource("SYSTEM")
                .sourceRefId(departmentId)
                .build()));
        when(profilePermissionRepository.findByProfileIdIn(List.of(profileId))).thenReturn(List.of());

        var access = accessResolutionService.resolveAccess(userId);

        assertThat(access.scopedDepartmentIds()).containsExactly(departmentId);
    }

    @Test
    @DisplayName("access response exposes empty department scope for self-service users")
    void resolveAccessExposesEmptyScopedDepartmentIdsForSelfScope() {
        UUID userId = UUID.randomUUID();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of());

        var access = accessResolutionService.resolveAccess(userId);

        assertThat(access.scopedDepartmentIds()).isEmpty();
    }

    @Test
    @DisplayName("resolves ST2I People navigation sections in product order")
    void resolvesPeopleNavigationSectionsInProductOrder() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID dashboardMenuId = UUID.randomUUID();
        UUID leaveMenuId = UUID.randomUUID();

        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("HR_CONSOLE")
            .displayKey("profile.hrConsole")
            .isActive(true)
            .build();
        MenuItem dashboardMenu = MenuItem.builder()
            .id(dashboardMenuId)
            .code("menu.workspace.dashboard")
            .translationKey("menu.workspace.dashboard")
            .sectionCode("WORKSPACE")
            .route("/dashboard")
            .icon("home")
            .displayOrder(10)
            .isActive(true)
            .build();
        MenuItem leaveMenu = MenuItem.builder()
            .id(leaveMenuId)
            .code("menu.timeOff.leaveRequests")
            .translationKey("menu.timeOff.leaveRequests")
            .sectionCode("TIME_OFF")
            .route("/leave")
            .icon("calendar")
            .displayOrder(10)
            .isActive(true)
            .build();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of(UserProfileAssignment.builder().profile(profile).profileId(profileId).build()));
        when(profileMenuAccessRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(
                ProfileMenuAccess.builder().menuItemId(leaveMenuId).build(),
                ProfileMenuAccess.builder().menuItemId(dashboardMenuId).build()
            ));
        when(menuItemRepository.findByIdIn(anyCollection())).thenReturn(List.of(leaveMenu, dashboardMenu));

        var navigation = accessResolutionService.resolveNavigation(userId);

        assertThat(navigation).extracting(section -> section.code())
            .containsExactly("WORKSPACE", "TIME_OFF");
        assertThat(navigation).extracting(section -> section.translationKey())
            .containsExactly("menu.section.workspace", "menu.section.timeOff");
    }

    @Test
    @DisplayName("matches permissions by resource and action")
    void matchesPermissionsByResourceAndAction() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("ADMIN_CONSOLE")
            .displayKey("profile.adminConsole")
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .name("AUDIT_LOG_READ")
            .resource("AUDIT_LOG")
            .action("READ")
            .scope("GLOBAL")
            .isActive(true)
            .build();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of(UserProfileAssignment.builder().profile(profile).profileId(profileId).build()));
        when(profilePermissionRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(ProfilePermission.builder().permission(permission).build()));

        assertThat(accessResolutionService.hasPermission(userId, "audit_log", "read")).isTrue();
        assertThat(accessResolutionService.hasPermissionName(userId, "AUDIT_LOG_READ")).isTrue();
    }

    // --- H1 regression: findByProfileIdIn must be called exactly once, not once per profile ---
    // TODO (H1 integration): supplement this with a Hibernate Statistics test on a real DB to
    //   assert PreparedStatement count <= 3 for a user with 50+ permissions across multiple profiles.

    @Test
    @DisplayName("hasPermission issues a single bulk fetch regardless of number of assigned profiles (H1)")
    void hasPermission_singleBulkFetchForManyProfiles() {
        UUID userId = UUID.randomUUID();
        UUID profileId1 = UUID.randomUUID();
        UUID profileId2 = UUID.randomUUID();
        UUID profileId3 = UUID.randomUUID();

        AccessProfile p1 = AccessProfile.builder().id(profileId1).code("P1").displayKey("p1").isActive(true).build();
        AccessProfile p2 = AccessProfile.builder().id(profileId2).code("P2").displayKey("p2").isActive(true).build();
        AccessProfile p3 = AccessProfile.builder().id(profileId3).code("P3").displayKey("p3").isActive(true).build();

        Permission permission = Permission.builder()
            .name("EMPLOYEE_READ").resource("EMPLOYEE").action("READ").scope("GLOBAL").isActive(true).build();

        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class)))
            .thenReturn(List.of(
                UserProfileAssignment.builder().profile(p1).profileId(profileId1).build(),
                UserProfileAssignment.builder().profile(p2).profileId(profileId2).build(),
                UserProfileAssignment.builder().profile(p3).profileId(profileId3).build()
            ));
        when(profilePermissionRepository.findByProfileIdIn(anyCollection()))
            .thenReturn(List.of(ProfilePermission.builder().permission(permission).build()));

        boolean result = accessResolutionService.hasPermission(userId, "EMPLOYEE", "READ");

        assertThat(result).isTrue();
        // The critical assertion: exactly ONE repository call for all profiles, not N=3 calls.
        verify(profilePermissionRepository, times(1)).findByProfileIdIn(anyCollection());
    }
}
