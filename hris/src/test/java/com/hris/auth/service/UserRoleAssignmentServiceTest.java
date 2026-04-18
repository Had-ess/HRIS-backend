package com.hris.auth.service;

import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.RoleAlreadyAssignedToUserException;
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
class UserRoleAssignmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private UserRoleAssignmentService userRoleAssignmentService;

    @Test
    @DisplayName("assign role to user works")
    void assignRoleToUserWorks() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build();
        UserRole userRole = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .role(role)
            .isActive(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.existsByUserIdAndRoleIdAndIsActiveTrue(userId, roleId)).thenReturn(false);
        when(userRoleRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(userRole));

        List<Role> response = userRoleAssignmentService.assignRole(userId, roleId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getCode()).isEqualTo("HR_ADMIN");
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("duplicate role assignment is rejected")
    void duplicateRoleAssignmentIsRejected() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(
            Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build()));
        when(userRoleRepository.existsByUserIdAndRoleIdAndIsActiveTrue(userId, roleId)).thenReturn(true);

        assertThatThrownBy(() -> userRoleAssignmentService.assignRole(userId, roleId))
            .isInstanceOf(RoleAlreadyAssignedToUserException.class)
            .hasMessage("Role is already assigned to this user");
    }

    @Test
    @DisplayName("remove role from user works")
    void removeRoleFromUserWorks() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserRole userRole = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .isActive(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(
            Role.builder().id(roleId).code("HR_ADMIN").name("HR Admin").isActive(true).build()));
        when(userRoleRepository.findByUserIdAndRoleIdAndIsActiveTrue(userId, roleId)).thenReturn(Optional.of(userRole));

        userRoleAssignmentService.removeRole(userId, roleId);

        assertThat(userRole.isActive()).isFalse();
        assertThat(userRole.getExpiresAt()).isNotNull();
        verify(userRoleRepository).save(userRole);
    }
}
