package com.hris.security;

import com.hris.auth.entity.Permission;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionAuthorizationService Unit Tests")
class PermissionAuthorizationServiceTest {

    @Mock
    private AccessScopeService accessScopeService;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionAuthorizationService permissionAuthorizationService;

    @Test
    @DisplayName("hasPermissionOrRole accepts fallback role from shared scope service")
    void hasPermissionOrRoleAcceptsFallbackRoleFromSharedScopeService() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Authentication authentication = new TestingAuthenticationToken(userId.toString(), null);
        authentication.setAuthenticated(true);

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of(
            UserRole.builder()
                .roleId(roleId)
                .role(Role.builder().code("ADMINISTRATION").isActive(true).build())
                .build()
        ));
        when(rolePermissionRepository.findByRoleIdIn(List.of(roleId))).thenReturn(List.of());

        boolean result = permissionAuthorizationService.hasPermissionOrRole(
            authentication, "PROJECT", "UPDATE", "ADMINISTRATION");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasPermission returns true when matching permission is granted")
    void hasPermissionReturnsTrueWhenMatchingPermissionIsGranted() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Authentication authentication = new TestingAuthenticationToken(userId.toString(), null);
        authentication.setAuthenticated(true);

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of(
            UserRole.builder()
                .roleId(roleId)
                .role(Role.builder().code("HR_ADMIN").isActive(true).build())
                .build()
        ));
        when(rolePermissionRepository.findByRoleIdIn(List.of(roleId))).thenReturn(List.of(
            RolePermission.builder().roleId(roleId).permissionId(permissionId).build()
        ));
        when(permissionRepository.findByIdInAndIsActiveTrue(Set.of(permissionId))).thenReturn(List.of(
            Permission.builder().id(permissionId).resource("dashboard").action("hr_view").isActive(true).build()
        ));

        boolean result = permissionAuthorizationService.hasPermission(authentication, "DASHBOARD", "HR_VIEW");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("authorize preserves access denied semantics")
    void authorizePreservesAccessDeniedSemantics() {
        UUID userId = UUID.randomUUID();
        Authentication authentication = new TestingAuthenticationToken(userId.toString(), null);
        authentication.setAuthenticated(true);

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> permissionAuthorizationService.authorize(
            authentication, "PROJECT", "UPDATE", "ADMINISTRATION"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessage("You do not have permission to perform this action");
    }
}
