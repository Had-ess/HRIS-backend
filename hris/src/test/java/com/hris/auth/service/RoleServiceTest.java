package com.hris.auth.service;

import com.hris.auth.entity.Role;
import com.hris.auth.repository.RoleRepository;
import com.hris.common.exception.InvalidRoleHierarchyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    @DisplayName("rejects self parent")
    void rejectsSelfParent() {
        UUID roleId = UUID.randomUUID();
        Role existing = Role.builder()
            .id(roleId)
            .code("HR_ADMIN")
            .name("HR Admin")
            .isActive(true)
            .build();
        Role update = Role.builder()
            .name("HR Admin")
            .code("HR_ADMIN")
            .isActive(true)
            .parentId(roleId)
            .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> roleService.update(roleId, update))
            .isInstanceOf(InvalidRoleHierarchyException.class)
            .hasMessage("Role cannot be its own parent");
    }

    @Test
    @DisplayName("rejects ancestry cycle across multiple roles")
    void rejectsCycleAcrossMultipleRoles() {
        UUID roleAId = UUID.randomUUID();
        UUID roleBId = UUID.randomUUID();
        UUID roleCId = UUID.randomUUID();

        Role roleA = Role.builder().id(roleAId).code("A").name("Role A").isActive(true).build();
        Role roleB = Role.builder().id(roleBId).code("B").name("Role B").isActive(true).parentId(roleAId).build();
        Role roleC = Role.builder().id(roleCId).code("C").name("Role C").isActive(true).parentId(roleBId).build();
        Role update = Role.builder()
            .name("Role A")
            .code("A")
            .isActive(true)
            .parentId(roleCId)
            .build();

        Map<UUID, Role> roles = Map.of(roleAId, roleA, roleBId, roleB, roleCId, roleC);
        when(roleRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(roles.get(invocation.getArgument(0))));

        assertThatThrownBy(() -> roleService.update(roleAId, update))
            .isInstanceOf(InvalidRoleHierarchyException.class)
            .hasMessage("Role hierarchy cycle detected");
    }

    @Test
    @DisplayName("allows valid parent assignment")
    void allowsValidParentAssignment() {
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        Role child = Role.builder()
            .id(childId)
            .code("CHILD")
            .name("Child")
            .isActive(true)
            .build();
        Role parent = Role.builder()
            .id(parentId)
            .code("PARENT")
            .name("Parent")
            .isActive(true)
            .build();
        Role update = Role.builder()
            .name("Child")
            .code("CHILD")
            .isActive(true)
            .parentId(parentId)
            .build();

        when(roleRepository.findById(eq(childId))).thenReturn(Optional.of(child));
        when(roleRepository.findById(eq(parentId))).thenReturn(Optional.of(parent));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role saved = roleService.update(childId, update);

        assertThat(saved.getParentId()).isEqualTo(parentId);
        verify(roleRepository).save(child);
    }
}
