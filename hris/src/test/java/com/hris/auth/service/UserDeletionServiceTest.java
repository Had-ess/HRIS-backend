package com.hris.auth.service;

import com.hris.access.service.UserAccessAssignmentService;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.repository.AuditLogRepository;
import com.hris.analytics.repository.ExportRecordRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAccessAssignmentService userAccessAssignmentService;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationEventRepository notificationEventRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ExportRecordRepository exportRecordRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private UserDeletionService userDeletionService;

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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(approvalStepRepository.existsByApproverId(userId)).thenReturn(false);
        when(fileAttachmentRepository.existsByUploadedById(userId)).thenReturn(false);
        when(adminRequestRepository.existsByRequesterUserId(userId)).thenReturn(false);
        when(adminRequestRepository.existsByProcessedByUserId(userId)).thenReturn(false);
        when(auditLogRepository.existsByActorId(userId)).thenReturn(false);
        when(exportRecordRepository.existsByExportedById(userId)).thenReturn(false);
        when(userAccessAssignmentService.getProfiles(userId)).thenReturn(List.of(
            new com.hris.access.dto.UserProfileSummaryDto(UUID.randomUUID(), "SELF_SERVICE", "profile.selfService", true)
        ));

        userDeletionService.deleteUser(userId, actorId);

        verify(keycloakAdminClient).deleteUser("kc-user-123");
        verify(notificationRepository).deleteByUserId(userId);
        verify(notificationEventRepository).deleteByTargetUserId(userId);
        verify(userAccessAssignmentService).removeProfile(eq(userId), any(UUID.class), eq(actorId));
        verify(userRepository).delete(user);
        verify(auditLogService).log(eq(actorId), eq(com.hris.analytics.enums.AuditAction.DELETE), eq("user"), eq(userId), eq(user), eq(null));
    }

    @Test
    @DisplayName("blocks self deletion")
    void blocksSelfDeletion() {
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> userDeletionService.deleteUser(actorId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("You cannot delete your own account");

        verify(userRepository, never()).findById(any());
    }
}
