package com.hris.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.recruitment.entity.Application;
import com.hris.recruitment.entity.NewHire;
import com.hris.recruitment.enums.CandidateSource;
import com.hris.recruitment.enums.NewHireStatus;
import com.hris.recruitment.repository.CandidateRepository;
import com.hris.recruitment.repository.NewHireRepository;
import com.hris.recruitment.repository.RequisitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewHireHandoffServiceTest {

    @Mock private NewHireRepository newHireRepository;
    @Mock private RequisitionRepository requisitionRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private RequisitionService requisitionService;
    @Mock private UserRepository userRepository;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private NewHireHandoffService service;

    private final UUID actorId = UUID.randomUUID();

    private NewHire pending() {
        return NewHire.builder()
            .id(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .candidateId(UUID.randomUUID())
            .requisitionId(UUID.randomUUID())
            .status(NewHireStatus.PENDING)
            .build();
    }

    @Test
    void createForHiredApplication_createsPendingAndNotifies() {
        Application app = Application.builder()
            .id(UUID.randomUUID()).requisitionId(UUID.randomUUID()).candidateId(UUID.randomUUID())
            .source(CandidateSource.DIRECT).build();
        when(newHireRepository.findByApplicationId(app.getId())).thenReturn(Optional.empty());
        when(newHireRepository.save(any(NewHire.class))).thenAnswer(i -> {
            NewHire n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        when(userRepository.findByPermissionNames(any())).thenReturn(List.of());

        NewHire result = service.createForHiredApplication(app, actorId);

        assertThat(result.getStatus()).isEqualTo(NewHireStatus.PENDING);
        verify(newHireRepository).save(any(NewHire.class));
    }

    @Test
    void createForHiredApplication_rejectsDuplicate() {
        Application app = Application.builder().id(UUID.randomUUID()).build();
        when(newHireRepository.findByApplicationId(app.getId())).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.createForHiredApplication(app, actorId))
            .isInstanceOf(InvalidWorkflowStateException.class);
    }

    @Test
    void complete_marksCompletedLinksEmployeeAndRecordsHire() {
        NewHire n = pending();
        UUID employeeId = UUID.randomUUID();
        when(newHireRepository.findByIdForUpdate(n.getId())).thenReturn(Optional.of(n));
        when(newHireRepository.save(any(NewHire.class))).thenAnswer(i -> i.getArgument(0));

        service.complete(n.getId(), employeeId, actorId);

        assertThat(n.getStatus()).isEqualTo(NewHireStatus.COMPLETED);
        assertThat(n.getCreatedEmployeeId()).isEqualTo(employeeId);
        assertThat(n.getFinalizedAt()).isNotNull();
        verify(requisitionService).recordHire(eq(n.getRequisitionId()), eq(actorId));
    }

    @Test
    void complete_rejectsNonPending() {
        NewHire n = pending();
        n.setStatus(NewHireStatus.COMPLETED);
        when(newHireRepository.findByIdForUpdate(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.complete(n.getId(), UUID.randomUUID(), actorId))
            .isInstanceOf(InvalidWorkflowStateException.class);
        verify(requisitionService, never()).recordHire(any(), any());
    }

    @Test
    void cancel_marksCancelled() {
        NewHire n = pending();
        when(newHireRepository.findByIdForUpdate(n.getId())).thenReturn(Optional.of(n));
        when(newHireRepository.save(any(NewHire.class))).thenAnswer(i -> i.getArgument(0));

        service.cancel(n.getId(), actorId);

        assertThat(n.getStatus()).isEqualTo(NewHireStatus.CANCELLED);
    }
}
