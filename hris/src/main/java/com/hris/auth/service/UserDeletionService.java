package com.hris.auth.service;

import com.hris.access.enums.StructuralEventType;
import com.hris.access.event.StructuralChangeEvent;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.analytics.repository.ExportRecordRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserRepository userRepository;
    private final UserAccessAssignmentService userAccessAssignmentService;
    private final ApprovalStepRepository approvalStepRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final ExportRecordRepository exportRecordRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void deleteUser(UUID userId, UUID actorId) {
        if (userId.equals(actorId)) {
            throw new IllegalStateException("You cannot delete your own account");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        boolean hasImmutableReferences = hasImmutableReferences(userId);

        if (user.getKeycloakId() != null && !user.getKeycloakId().isBlank()) {
            keycloakAdminClient.deleteUser(user.getKeycloakId());
        }

        notificationRepository.deleteByUserId(userId);
        notificationEventRepository.deleteByTargetUserId(userId);
        applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
            StructuralEventType.EMPLOYEE_OFFBOARDED, userId, userId, actorId));
        userAccessAssignmentService.getProfiles(userId)
            .forEach(profile -> userAccessAssignmentService.removeProfile(userId, profile.id(), actorId));

        if (hasImmutableReferences) {
            archiveUser(user);
            userRepository.save(user);
            auditLogService.log(actorId, AuditAction.UPDATE, "user",
                userId, null, user);
            return;
        }

        userRepository.delete(user);

        auditLogService.log(actorId, AuditAction.DELETE, "user", userId, user, null);
    }

    private boolean hasImmutableReferences(UUID userId) {
        return approvalStepRepository.existsByApproverId(userId)
            || fileAttachmentRepository.existsByUploadedById(userId)
            || adminRequestRepository.existsByRequesterUserId(userId)
            || adminRequestRepository.existsByProcessedByUserId(userId)
            || auditLogRepository.existsByActorId(userId)
            || exportRecordRepository.existsByExportedById(userId);
    }

    private void archiveUser(User user) {
        UUID userId = user.getId();
        user.setActive(false);
        user.setSeed(false);
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setEmail("deleted+" + userId + "@hris.local");
        user.setKeycloakId("deleted-" + userId);
    }
}
