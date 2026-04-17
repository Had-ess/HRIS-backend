package com.hris.auth.service;

import com.hris.auth.dto.PermissionCreateDto;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.dto.PermissionUpdateDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.common.exception.PermissionDeletionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    @DisplayName("create permission works")
    void createPermissionWorks() {
        PermissionCreateDto dto = new PermissionCreateDto(
            "department_create",
            "department",
            "create",
            "Can create departments",
            true
        );

        when(permissionRepository.existsByNameIgnoreCase("DEPARTMENT_CREATE")).thenReturn(false);
        when(permissionRepository.existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCase(
            "DEPARTMENT", "CREATE", "GLOBAL")).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
            Permission permission = invocation.getArgument(0);
            permission.setId(UUID.randomUUID());
            return permission;
        });

        PermissionResponseDto response = permissionService.create(dto);

        assertThat(response.name()).isEqualTo("DEPARTMENT_CREATE");
        assertThat(response.resource()).isEqualTo("DEPARTMENT");
        assertThat(response.action()).isEqualTo("CREATE");
        assertThat(response.description()).isEqualTo("Can create departments");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("update permission works")
    void updatePermissionWorks() {
        UUID permissionId = UUID.randomUUID();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("DEPARTMENT_CREATE")
            .resource("DEPARTMENT")
            .action("CREATE")
            .scope("GLOBAL")
            .description("Initial description")
            .isActive(true)
            .build();
        PermissionUpdateDto dto = new PermissionUpdateDto(
            "department_update",
            "department",
            "update",
            "Updated description",
            false
        );

        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(permissionRepository.existsByNameIgnoreCaseAndIdNot("DEPARTMENT_UPDATE", permissionId)).thenReturn(false);
        when(permissionRepository.existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCaseAndIdNot(
            "DEPARTMENT", "UPDATE", "GLOBAL", permissionId)).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionResponseDto response = permissionService.update(permissionId, dto);

        assertThat(response.name()).isEqualTo("DEPARTMENT_UPDATE");
        assertThat(response.action()).isEqualTo("UPDATE");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(response.active()).isFalse();
    }

    @Test
    @DisplayName("cannot delete permission assigned to a role")
    void cannotDeletePermissionAssignedToRole() {
        UUID permissionId = UUID.randomUUID();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name("DEPARTMENT_DELETE")
            .resource("DEPARTMENT")
            .action("DELETE")
            .scope("GLOBAL")
            .build();

        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(rolePermissionRepository.existsByPermissionId(permissionId)).thenReturn(true);

        assertThatThrownBy(() -> permissionService.delete(permissionId))
            .isInstanceOf(PermissionDeletionNotAllowedException.class)
            .hasMessage("Permission cannot be deleted because it is assigned to one or more roles");

        verify(rolePermissionRepository).existsByPermissionId(permissionId);
    }
}
