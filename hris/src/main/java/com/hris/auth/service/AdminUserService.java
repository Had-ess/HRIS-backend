package com.hris.auth.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.analytics.repository.ExportRecordRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.AdminUserCreateDto;
import com.hris.auth.dto.AdminUserResponseDto;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AccessResolutionService accessResolutionService;
    private final UserAccessAssignmentService userAccessAssignmentService;
    private final AccountProvisioningService accountProvisioningService;
    private final EmployeeRepository employeeRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final ExportRecordRepository exportRecordRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AdminUserResponseDto> getAll() {
        return userRepository.findAll().stream()
            .sorted((left, right) -> left.getEmail().compareToIgnoreCase(right.getEmail()))
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public AdminUserResponseDto create(AdminUserCreateDto dto, UUID actorId) {
        if (dto.profileIds() == null || dto.profileIds().isEmpty()) {
            throw new IllegalArgumentException("At least one access profile must be assigned");
        }

        User saved = accountProvisioningService.provision(new AccountProvisioningRequest(
            dto.username(),
            dto.email(),
            dto.firstName(),
            dto.lastName(),
            dto.password(),
            dto.temporaryPassword() != null && dto.temporaryPassword(),
            dto.profileIds()
        ), actorId);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID actorId) {
        if (userId.equals(actorId)) {
            throw new IllegalStateException("You cannot delete your own account");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        validateDeletionAllowed(userId);

        if (user.getKeycloakId() != null && !user.getKeycloakId().isBlank()) {
            keycloakAdminClient.deleteUser(user.getKeycloakId());
        }

        notificationRepository.deleteByUserId(userId);
        notificationEventRepository.deleteByTargetUserId(userId);
        userAccessAssignmentService.getProfiles(userId)
            .forEach(profile -> userAccessAssignmentService.removeProfile(userId, profile.id(), actorId));
        userRepository.delete(user);

        auditLogService.log(actorId, AuditAction.DELETE, "user", userId, user, null);
    }

    private AdminUserResponseDto toDto(User user) {
        List<String> profiles = accessResolutionService.getEffectiveProfiles(user.getId()).stream()
            .map(profile -> profile.getCode())
            .sorted()
            .toList();

        return new AdminUserResponseDto(
            user.getId(),
            user.getKeycloakId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getLastLogin(),
            profiles
        );
    }

    private void validateDeletionAllowed(UUID userId) {
        if (employeeRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("User cannot be deleted because it is linked to an employee profile");
        }
        if (approvalStepRepository.existsByApproverId(userId)) {
            throw new IllegalStateException("User cannot be deleted because it is referenced in approval history");
        }
        if (fileAttachmentRepository.existsByUploadedById(userId)) {
            throw new IllegalStateException("User cannot be deleted because it is referenced by uploaded files");
        }
        if (adminRequestRepository.existsByRequesterId(userId) || adminRequestRepository.existsByResolvedById(userId)) {
            throw new IllegalStateException("User cannot be deleted because it is referenced by administrative requests");
        }
        if (auditLogRepository.existsByActorId(userId)) {
            throw new IllegalStateException("User cannot be deleted because it is referenced in audit history");
        }
        if (exportRecordRepository.existsByExportedById(userId)) {
            throw new IllegalStateException("User cannot be deleted because it is referenced by exported reports");
        }
    }
}
