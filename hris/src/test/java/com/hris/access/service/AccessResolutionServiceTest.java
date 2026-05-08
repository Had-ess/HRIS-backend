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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
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
            .thenReturn(List.of(UserProfileAssignment.builder().profile(profile).profileId(profileId).build()));
        when(profilePermissionRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(ProfilePermission.builder().permission(permission).permissionId(permissionId).build()));
        when(profileMenuAccessRepository.findByProfileIdIn(List.of(profileId)))
            .thenReturn(List.of(ProfileMenuAccess.builder().menuItemId(menuId).build()));
        when(menuItemRepository.findByIdIn(anyCollection())).thenReturn(List.of(menuItem));

        var access = accessResolutionService.resolveAccess(userId);
        var navigation = accessResolutionService.resolveNavigation(userId);

        assertThat(access.profileCodes()).containsExactly("HR_CONSOLE");
        assertThat(access.permissions()).extracting(permissionDto -> permissionDto.name())
            .containsExactly("ACCESS_PROFILE_READ");
        assertThat(navigation).hasSize(1);
        assertThat(navigation.getFirst().items()).extracting(item -> item.code())
            .containsExactly("menu.workspace.dashboard");
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
}
