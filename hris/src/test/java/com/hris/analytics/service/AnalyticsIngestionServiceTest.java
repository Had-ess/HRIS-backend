package com.hris.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.entity.AnalyticsEvent;
import com.hris.analytics.entity.LeaveFact;
import com.hris.analytics.enums.AnalyticsEventType;
import com.hris.analytics.repository.AnalyticsEventRepository;
import com.hris.analytics.repository.ApprovalFactRepository;
import com.hris.analytics.repository.LeaveFactRepository;
import com.hris.leave.enums.LeaveStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsIngestionServiceTest {

    @Mock private AnalyticsEventRepository analyticsEventRepository;
    @Mock private LeaveFactRepository leaveFactRepository;
    @Mock private ApprovalFactRepository approvalFactRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ingest leave event upserts leave fact and marks event processed")
    void ingestLeaveEventUpsertsLeaveFact() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();

        AnalyticsEvent event = AnalyticsEvent.builder()
            .id(UUID.randomUUID())
            .eventType(AnalyticsEventType.LEAVE_APPROVED)
            .aggregateType("LEAVE_REQUEST")
            .aggregateId(requestId)
            .occurredAt(Instant.parse("2026-05-07T10:00:00Z"))
            .eventDate(LocalDate.of(2026, 5, 7))
            .payload(objectMapper.writeValueAsString(java.util.Map.of(
                "leaveRequestId", requestId.toString(),
                "employeeId", employeeId.toString(),
                "departmentId", departmentId.toString(),
                "leaveTypeId", leaveTypeId.toString(),
                "workingDays", 3,
                "leaveStatus", "APPROVED",
                "submittedAt", "2026-05-05T10:00:00Z",
                "completedAt", "2026-05-07T10:00:00Z"
            )))
            .build();

        when(leaveFactRepository.findByLeaveRequestId(requestId)).thenReturn(Optional.empty());

        AnalyticsIngestionService analyticsIngestionService = new AnalyticsIngestionService(
            analyticsEventRepository,
            leaveFactRepository,
            approvalFactRepository,
            objectMapper
        );

        analyticsIngestionService.ingest(event);

        ArgumentCaptor<LeaveFact> factCaptor = ArgumentCaptor.forClass(LeaveFact.class);
        verify(leaveFactRepository).save(factCaptor.capture());
        LeaveFact saved = factCaptor.getValue();

        assertThat(saved.getLeaveRequestId()).isEqualTo(requestId);
        assertThat(saved.getEmployeeId()).isEqualTo(employeeId);
        assertThat(saved.getDepartmentId()).isEqualTo(departmentId);
        assertThat(saved.getLeaveTypeId()).isEqualTo(leaveTypeId);
        assertThat(saved.getWorkingDays()).isEqualTo(3);
        assertThat(saved.getRequestStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(saved.getApprovalDurationDays()).isEqualTo(2);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(analyticsEventRepository).save(event);
    }
}
