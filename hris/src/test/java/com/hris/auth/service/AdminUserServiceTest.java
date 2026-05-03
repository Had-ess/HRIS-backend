package com.hris.auth.service;

import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.analytics.repository.ExportRecordRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.AdminUserCreateDto;
import com.hris.auth.dto.AdminUserResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.notification.repository.NotificationRepository;
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
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationEventRepository notificationEventRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ExportRecordRepository exportRecordRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;

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

    @Test
    @DisplayName("deletes standalone user and cleans direct relations")
    void deletesStandaloneUserAndCleansRelations() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .keycloakId("kc-user-123")
            .email("remove.me@demo.hris.local")
            .firstName("Remove")
            .lastName("Me")
            .isActive(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(employeeRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());
        when(approvalStepRepository.existsByApproverId(userId)).thenReturn(false);
        when(fileAttachmentRepository.existsByUploadedById(userId)).thenReturn(false);
        when(adminRequestRepository.existsByRequesterId(userId)).thenReturn(false);
        when(adminRequestRepository.existsByResolvedById(userId)).thenReturn(false);
        when(rolePermissionRepository.existsByGrantedById(userId)).thenReturn(false);
        when(auditLogRepository.existsByActorId(userId)).thenReturn(false);
        when(exportRecordRepository.existsByExportedById(userId)).thenReturn(false);

        adminUserService.delete(userId, actorId);

        verify(keycloakAdminClient).deleteUser("kc-user-123");
        verify(notificationRepository).deleteByUserId(userId);
        verify(notificationEventRepository).deleteByTargetUserId(userId);
        verify(userRoleRepository).deleteByUserId(userId);
        verify(userRepository).delete(user);
        verify(auditLogService).log(eq(actorId), eq(com.hris.analytics.enums.AuditAction.DELETE), eq("user"), eq(userId), eq(user), eq(null));
    }

    @Test
    @DisplayName("blocks deleting account linked to employee profile")
    void blocksDeletingLinkedEmployeeAccount() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .keycloakId("kc-user-123")
            .email("employee@demo.hris.local")
            .firstName("Employee")
            .lastName("User")
            .build();

        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        when(employeeRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(Employee.builder().id(UUID.randomUUID()).userId(userId).build()));

        assertThatThrownBy(() -> adminUserService.delete(userId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("User cannot be deleted because it is linked to an employee profile");
    }

    @Test
    @DisplayName("blocks self deletion")
    void blocksSelfDeletion() {
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> adminUserService.delete(actorId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You cannot delete your own account");
    }
}
