package com.hris.access.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.dto.AccessProfileUpdateDto;
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
import static org.mockito.Mockito.atLeastOnce;
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
    @DisplayName("update modifies editable access profile metadata")
    void updateModifiesEditableProfileMetadata() {
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("OLD_PROFILE")
            .displayKey("profile.old")
            .descriptionKey("profile.old.description")
            .isActive(true)
            .build();

        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(accessProfileRepository.existsByCodeIgnoreCaseAndIdNot("NEW_PROFILE", profileId)).thenReturn(false);
        when(accessProfileRepository.save(profile)).thenReturn(profile);
        when(userProfileAssignmentRepository.countByProfileIdAndIsActiveTrue(profileId)).thenReturn(0L);

        var result = accessProfileService.update(
            profileId,
            new AccessProfileUpdateDto(" new_profile ", "profile.new", "", false),
            actorId
        );

        assertThat(result.code()).isEqualTo("NEW_PROFILE");
        assertThat(result.displayKey()).isEqualTo("profile.new");
        assertThat(result.descriptionKey()).isNull();
        assertThat(result.active()).isFalse();
        verify(accessProfileRepository).save(profile);
        verify(auditLogService).log(eq(actorId), any(), eq("access_profile"), eq(profileId), any(), any());
    }

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

    @Test
    @DisplayName("assignMenus grants permissions required by selected page menus")
    void assignMenusGrantsRequiredPagePermissions() {
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("MANAGER_INBOX")
            .displayKey("profile.seed.MANAGER_INBOX")
            .isActive(true)
            .build();
        MenuItem menuItem = MenuItem.builder()
            .id(menuId)
            .code("menu.administration.employees")
            .translationKey("menu.people.employees")
            .sectionCode("PEOPLE")
            .route("/employees")
            .icon("users")
            .displayOrder(5)
            .isActive(true)
            .build();
        Permission employeeRead = Permission.builder()
            .id(permissionId)
            .name("EMPLOYEE_READ")
            .resource("EMPLOYEE")
            .action("READ")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission employeeManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("EMPLOYEE_MANAGE")
            .resource("EMPLOYEE")
            .action("MANAGE")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        Permission departmentRead = Permission.builder()
            .id(UUID.randomUUID())
            .name("DEPARTMENT_READ")
            .resource("DEPARTMENT")
            .action("READ")
            .scope("SCOPED")
            .isActive(true)
            .build();
        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(menuItemRepository.findById(menuId)).thenReturn(Optional.of(menuItem));
        when(profilePermissionRepository.findByProfileId(profileId)).thenReturn(List.of());
        when(permissionRepository.findByNameIgnoreCase("EMPLOYEE_READ")).thenReturn(Optional.of(employeeRead));
        when(permissionRepository.findByNameIgnoreCase("EMPLOYEE_MANAGE")).thenReturn(Optional.of(employeeManage));
        when(permissionRepository.findByNameIgnoreCase("DEPARTMENT_READ")).thenReturn(Optional.of(departmentRead));
        when(profileMenuAccessRepository.findByProfileId(profileId)).thenReturn(List.of(
            ProfileMenuAccess.builder().profileId(profileId).menuItemId(menuId).build()
        ));
        when(menuItemRepository.findByIdIn(List.of(menuId))).thenReturn(List.of(menuItem));

        accessProfileService.assignMenus(profileId, List.of(menuId), actorId);

        verify(profilePermissionRepository, atLeastOnce()).save(any(ProfilePermission.class));
    }

    @Test
    @DisplayName("assignMenus grants department read for people and project pages")
    void assignMenusGrantsDepartmentReadForPeoplePages() {
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID employeeMenuId = UUID.randomUUID();
        UUID teamMenuId = UUID.randomUUID();
        UUID projectMenuId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("MANAGER_INBOX")
            .displayKey("profile.seed.MANAGER_INBOX")
            .isActive(true)
            .build();
        MenuItem employeeMenu = MenuItem.builder()
            .id(employeeMenuId)
            .code("menu.administration.employees")
            .translationKey("menu.people.employees")
            .sectionCode("PEOPLE")
            .route("/employees")
            .icon("users")
            .displayOrder(5)
            .isActive(true)
            .build();
        MenuItem teamMenu = MenuItem.builder()
            .id(teamMenuId)
            .code("menu.administration.teams")
            .translationKey("menu.people.teams")
            .sectionCode("PEOPLE")
            .route("/admin/teams")
            .icon("users")
            .displayOrder(10)
            .isActive(true)
            .build();
        MenuItem projectMenu = MenuItem.builder()
            .id(projectMenuId)
            .code("menu.settings.projects")
            .translationKey("menu.people.projects")
            .sectionCode("PEOPLE")
            .route("/settings/projects")
            .icon("briefcase")
            .displayOrder(15)
            .isActive(true)
            .build();
        Permission employeeRead = Permission.builder()
            .id(UUID.randomUUID())
            .name("EMPLOYEE_READ")
            .resource("EMPLOYEE")
            .action("READ")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission teamRead = Permission.builder()
            .id(UUID.randomUUID())
            .name("TEAM_READ")
            .resource("TEAM")
            .action("READ")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission projectManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("PROJECT_PORTFOLIO_MANAGE")
            .resource("PROJECT")
            .action("PORTFOLIO_MANAGE")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        Permission projectAssignmentManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("PROJECT_ASSIGNMENT_MANAGE")
            .resource("PROJECT")
            .action("ASSIGNMENT_MANAGE")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission teamManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("TEAM_MANAGE")
            .resource("TEAM")
            .action("MANAGE")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission departmentRead = Permission.builder()
            .id(UUID.randomUUID())
            .name("DEPARTMENT_READ")
            .resource("DEPARTMENT")
            .action("READ")
            .scope("SCOPED")
            .isActive(true)
            .build();
        Permission departmentManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("DEPARTMENT_MANAGE")
            .resource("DEPARTMENT")
            .action("MANAGE")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        Permission employeeManage = Permission.builder()
            .id(UUID.randomUUID())
            .name("EMPLOYEE_MANAGE")
            .resource("EMPLOYEE")
            .action("MANAGE")
            .scope("GLOBAL")
            .isActive(true)
            .build();

        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(menuItemRepository.findById(employeeMenuId)).thenReturn(Optional.of(employeeMenu));
        when(menuItemRepository.findById(teamMenuId)).thenReturn(Optional.of(teamMenu));
        when(menuItemRepository.findById(projectMenuId)).thenReturn(Optional.of(projectMenu));
        when(profilePermissionRepository.findByProfileId(profileId)).thenReturn(List.of());
        when(permissionRepository.findByNameIgnoreCase("EMPLOYEE_READ")).thenReturn(Optional.of(employeeRead));
        when(permissionRepository.findByNameIgnoreCase("TEAM_READ")).thenReturn(Optional.of(teamRead));
        when(permissionRepository.findByNameIgnoreCase("TEAM_MANAGE")).thenReturn(Optional.of(teamManage));
        when(permissionRepository.findByNameIgnoreCase("PROJECT_PORTFOLIO_MANAGE")).thenReturn(Optional.of(projectManage));
        when(permissionRepository.findByNameIgnoreCase("PROJECT_ASSIGNMENT_MANAGE")).thenReturn(Optional.of(projectAssignmentManage));
        when(permissionRepository.findByNameIgnoreCase("DEPARTMENT_READ")).thenReturn(Optional.of(departmentRead));
        when(permissionRepository.findByNameIgnoreCase("DEPARTMENT_MANAGE")).thenReturn(Optional.of(departmentManage));
        when(permissionRepository.findByNameIgnoreCase("EMPLOYEE_MANAGE")).thenReturn(Optional.of(employeeManage));
        when(profileMenuAccessRepository.findByProfileId(profileId)).thenReturn(List.of(
            ProfileMenuAccess.builder().profileId(profileId).menuItemId(employeeMenuId).build(),
            ProfileMenuAccess.builder().profileId(profileId).menuItemId(teamMenuId).build(),
            ProfileMenuAccess.builder().profileId(profileId).menuItemId(projectMenuId).build()
        ));
        when(menuItemRepository.findByIdIn(List.of(employeeMenuId, teamMenuId, projectMenuId))).thenReturn(List.of(employeeMenu, teamMenu, projectMenu));

        accessProfileService.assignMenus(profileId, List.of(employeeMenuId, teamMenuId, projectMenuId), actorId);

        verify(profilePermissionRepository, atLeastOnce()).save(any(ProfilePermission.class));
    }
}
