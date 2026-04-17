package com.hris.auth.service;

import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.entity.User;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.PermissionAlreadyAssignedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    @Test
    @DisplayName("assign permission to role works")
    void assignPermissionToRoleWorks() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Role role = Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("ROLE_ASSIGN_PERMISSION")
            .resource("ROLE")
            .action("ASSIGN_PERMISSION")
            .scope("GLOBAL")
            .isActive(true)
            .build();
        RolePermission assigned = RolePermission.builder()
            .roleId(roleId)
            .permissionId(permissionId)
            .permission(permission)
            .grantedById(actorId)
            .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(User.builder().id(actorId).build()));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)).thenReturn(false);
        when(rolePermissionRepository.findByRoleId(roleId)).thenReturn(List.of(assigned));

        List<PermissionResponseDto> response = rolePermissionService.assignPermissions(
            roleId,
            List.of(permissionId),
            actorId
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().name()).isEqualTo("ROLE_ASSIGN_PERMISSION");
        verify(rolePermissionRepository).saveAll(any());
    }

    @Test
    @DisplayName("duplicate permission assignment is rejected")
    void duplicatePermissionAssignmentIsRejected() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Role role = Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("ROLE_ASSIGN_PERMISSION")
            .resource("ROLE")
            .action("ASSIGN_PERMISSION")
            .scope("GLOBAL")
            .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(User.builder().id(actorId).build()));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)).thenReturn(true);

        assertThatThrownBy(() -> rolePermissionService.assignPermissions(roleId, List.of(permissionId), actorId))
            .isInstanceOf(PermissionAlreadyAssignedException.class)
            .hasMessage("Permission is already assigned to this role");
    }

    @Test
    @DisplayName("remove permission from role works")
    void removePermissionFromRoleWorks() {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        RolePermission link = RolePermission.builder()
            .id(UUID.randomUUID())
            .roleId(roleId)
            .permissionId(permissionId)
            .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(
            Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build()));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(
            Permission.builder().id(permissionId).name("ROLE_ASSIGN_PERMISSION").resource("ROLE").action("ASSIGN_PERMISSION").scope("GLOBAL").build()));
        when(rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)).thenReturn(Optional.of(link));

        rolePermissionService.removePermission(roleId, permissionId);

        verify(rolePermissionRepository).delete(link);
    }
}
