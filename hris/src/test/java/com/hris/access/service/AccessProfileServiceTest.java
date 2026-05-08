package com.hris.access.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.MenuItem;
import com.hris.access.entity.ProfileMenuAccess;
import com.hris.access.entity.ProfilePermission;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.MenuItemRepository;
import com.hris.access.repository.ProfileMenuAccessRepository;
import com.hris.access.repository.ProfilePermissionRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Permission;
import com.hris.auth.repository.PermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessProfileServiceTest {

    @Mock
    private AccessProfileRepository accessProfileRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private ProfilePermissionRepository profilePermissionRepository;

    @Mock
    private ProfileMenuAccessRepository profileMenuAccessRepository;

    @Mock
    private UserProfileAssignmentRepository userProfileAssignmentRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AccessProfileService accessProfileService;

    @Test
    @DisplayName("assignPermissions replaces profile permission assignments")
    void assignPermissionsReplacesAssignments() {
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("HR_CONSOLE")
            .displayKey("profile.seed.HR_CONSOLE")
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("ACCESS_PROFILE_READ")
            .resource("ACCESS_PROFILE")
            .action("READ")
            .isActive(true)
            .build();

        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(profilePermissionRepository.findByProfileId(profileId)).thenReturn(List.of(
            ProfilePermission.builder().profileId(profileId).permissionId(permissionId).build()
        ));
        when(permissionRepository.findByIdInAndIsActiveTrue(List.of(permissionId))).thenReturn(List.of(permission));

        var result = accessProfileService.assignPermissions(profileId, List.of(permissionId), actorId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("ACCESS_PROFILE_READ");
        verify(profilePermissionRepository).deleteByProfileId(profileId);
        verify(profilePermissionRepository).save(any(ProfilePermission.class));
        verify(auditLogService).log(eq(actorId), any(), eq("access_profile_permission"), eq(profileId), eq(null), eq(List.of(permissionId)));
    }

    @Test
    @DisplayName("assignMenus replaces visible menu assignments")
    void assignMenusReplacesAssignments() {
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.seed.SELF_SERVICE")
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

        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(menuItemRepository.findById(menuId)).thenReturn(Optional.of(menuItem));
        when(profileMenuAccessRepository.findByProfileId(profileId)).thenReturn(List.of(
            ProfileMenuAccess.builder().profileId(profileId).menuItemId(menuId).build()
        ));
        when(menuItemRepository.findByIdIn(List.of(menuId))).thenReturn(List.of(menuItem));

        var result = accessProfileService.assignMenus(profileId, List.of(menuId), actorId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("menu.workspace.dashboard");
        verify(profileMenuAccessRepository).deleteByProfileId(profileId);
        verify(profileMenuAccessRepository).save(any(ProfileMenuAccess.class));
        verify(auditLogService).log(eq(actorId), any(), eq("access_profile_menu"), eq(profileId), eq(null), eq(List.of(menuId)));
    }
}
