package com.hris.auth.service;

import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.AdminUserCreateDto;
import com.hris.auth.dto.AdminUserResponseDto;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AccountProvisioningService accountProvisioningService;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    @DisplayName("creates user through shared account provisioning")
    void createsUserThroughSharedAccountProvisioning() {
        UUID actorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Role role = Role.builder().id(roleId).code("EMPLOYEE").name("Employee").isActive(true).build();
        User savedUser = User.builder()
            .id(UUID.randomUUID())
            .keycloakId("kc-user-123")
            .email("new.user@demo.hris.local")
            .firstName("New")
            .lastName("User")
            .createdAt(Instant.now())
            .isActive(true)
            .build();

        when(accountProvisioningService.provision(any(AccountProvisioningRequest.class), eq(actorId))).thenReturn(savedUser);
        when(userRoleRepository.findEffectiveByUserId(eq(savedUser.getId()), any())).thenReturn(List.of(
            UserRole.builder().role(role).build()
        ));

        AdminUserResponseDto result = adminUserService.create(new AdminUserCreateDto(
            "new.user",
            "new.user@demo.hris.local",
            "New",
            "User",
            "Temp123!",
            false,
            List.of(roleId)
        ), actorId);

        assertThat(result.email()).isEqualTo("new.user@demo.hris.local");
        assertThat(result.roles()).containsExactly("EMPLOYEE");
        verify(accountProvisioningService).provision(any(AccountProvisioningRequest.class), eq(actorId));
    }

    @Test
    @DisplayName("requires at least one role")
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> adminUserService.create(new AdminUserCreateDto(
            "new.user",
            "new.user@demo.hris.local",
            "New",
            "User",
            "Temp123!",
            false,
            List.of()
        ), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("At least one role must be assigned");
    }
}
