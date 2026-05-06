package com.hris.auth.controller;

import com.hris.auth.dto.DepartmentCreateDto;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.auth.service.DepartmentService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.service.AccessScopeService;
import com.hris.support.TestAuthenticationFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AccessScopeService accessScopeService;

    @Test
    @DisplayName("protected endpoint is denied without required permission")
    void protectedEndpointDeniedWithoutRequiredPermission() {
        PermissionAuthorizationService authorizationService = new PermissionAuthorizationService(
            accessScopeService,
            rolePermissionRepository,
            permissionRepository
        );
        DepartmentController controller = new DepartmentController(departmentService, authorizationService);
        JwtAuthenticationToken authentication = TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID());

        assertThatThrownBy(() -> controller.create(
            new DepartmentCreateDto("HR", "HR", null, true),
            authentication))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("You do not have permission to perform this action");
    }

    @Test
    @DisplayName("protected endpoint succeeds with required permission")
    void protectedEndpointSucceedsWithRequiredPermission() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        JwtAuthenticationToken authentication = TestAuthenticationFactory.jwtAuthentication(userId);
        Role role = Role.builder().id(roleId).code("CUSTOM_ADMIN").name("Custom Admin").isActive(true).build();
        UserRole userRole = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .role(role)
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("DEPARTMENT_CREATE")
            .resource("DEPARTMENT")
            .action("CREATE")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        DepartmentDto responseDto = new DepartmentDto(UUID.randomUUID(), "HR", "HR", null, true, 0L, 0L, 0L);

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of(userRole));
        when(rolePermissionRepository.findByRoleIdIn(List.of(roleId))).thenReturn(List.of(
            RolePermission.builder().id(UUID.randomUUID()).roleId(roleId).permissionId(permissionId).build()
        ));
        when(permissionRepository.findByIdInAndIsActiveTrue(java.util.Set.of(permissionId))).thenReturn(List.of(permission));
        when(departmentService.create(any(DepartmentCreateDto.class), eq(userId))).thenReturn(responseDto);

        PermissionAuthorizationService authorizationService = new PermissionAuthorizationService(
            accessScopeService,
            rolePermissionRepository,
            permissionRepository
        );
        DepartmentController controller = new DepartmentController(departmentService, authorizationService);

        ResponseEntity<ApiResponse<DepartmentDto>> response = controller.create(
            new DepartmentCreateDto("HR", "HR", null, true),
            authentication
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().name()).isEqualTo("HR");
        verify(departmentService).create(any(DepartmentCreateDto.class), eq(userId));
    }

    @Test
    @DisplayName("administration fallback role can manage departments without explicit permission")
    void administrationFallbackRoleCanManageDepartments() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DepartmentDto responseDto = new DepartmentDto(UUID.randomUUID(), "Finance", "FIN", null, true, 0L, 0L, 0L);
        Role administrationRole = Role.builder()
            .id(roleId)
            .code("ADMINISTRATION")
            .name("Administration")
            .isActive(true)
            .build();
        UserRole administrationAssignment = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .role(administrationRole)
            .isActive(true)
            .build();

        when(departmentService.create(any(DepartmentCreateDto.class), eq(userId))).thenReturn(responseDto);
        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of(administrationAssignment));

        PermissionAuthorizationService authorizationService = new PermissionAuthorizationService(
            accessScopeService,
            rolePermissionRepository,
            permissionRepository
        );
        DepartmentController controller = new DepartmentController(departmentService, authorizationService);

        ResponseEntity<ApiResponse<DepartmentDto>> response = controller.create(
            new DepartmentCreateDto("Finance", "FIN", null, true),
            TestAuthenticationFactory.jwtAuthentication(userId, "ADMINISTRATION")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().code()).isEqualTo("FIN");
        verify(departmentService).create(any(DepartmentCreateDto.class), eq(userId));
    }

    @Test
    @DisplayName("department manager fallback role can read scoped departments")
    void departmentManagerFallbackRoleCanReadScopedDepartments() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role managerRole = Role.builder()
            .id(roleId)
            .code("DEPT_MANAGER")
            .name("Department Manager")
            .isActive(true)
            .build();
        UserRole managerAssignment = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .role(managerRole)
            .isActive(true)
            .build();
        DepartmentDto responseDto = new DepartmentDto(
            UUID.randomUUID(),
            "Engineering",
            "ENG",
            null,
            true,
            7L,
            3L,
            11L
        );
        PageRequest pageable = PageRequest.of(0, 20);

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of(managerAssignment));
        when(departmentService.getAll(eq(userId), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));

        PermissionAuthorizationService authorizationService = new PermissionAuthorizationService(
            accessScopeService,
            rolePermissionRepository,
            permissionRepository
        );
        DepartmentController controller = new DepartmentController(departmentService, authorizationService);

        ResponseEntity<ApiResponse<PageResponse<DepartmentDto>>> response = controller.getAll(
            pageable,
            TestAuthenticationFactory.jwtAuthentication(userId, "DEPT_MANAGER")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).employeeCount()).isEqualTo(7L);
        verify(departmentService).getAll(eq(userId), eq(pageable));
    }
}
