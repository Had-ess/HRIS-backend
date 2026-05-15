package com.hris.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.entity.AnalyticsEvent;
import com.hris.analytics.entity.ApprovalFact;
import com.hris.analytics.entity.LeaveFact;
import com.hris.analytics.enums.AnalyticsEventType;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.analytics.repository.AnalyticsEventRepository;
import com.hris.analytics.repository.ApprovalFactRepository;
import com.hris.analytics.repository.LeaveFactRepository;
import com.hris.approval.enums.StepStatus;
import com.hris.leave.enums.LeaveStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsIngestionService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final LeaveFactRepository leaveFactRepository;
    private final ApprovalFactRepository approvalFactRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "analyticsIngestionJob", lockAtMostFor = "PT3M", lockAtLeastFor = "PT30S")
    @Transactional
    public void ingestPending() {
        List<AnalyticsEvent> events = analyticsEventRepository.findPending(PageRequest.of(0, 200));
        for (AnalyticsEvent event : events) {
            ingest(event);
        }
    }

    @Transactional
    public void ingest(AnalyticsEvent event) {
        try {
            switch (event.getEventType()) {
                case LEAVE_SUBMITTED, LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_CANCELLED -> ingestLeaveEvent(event);
                case APPROVAL_STEP_APPROVED, APPROVAL_STEP_REJECTED -> ingestApprovalEvent(event);
                default -> {
                }
            }
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
        } catch (RuntimeException ex) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(ex.getMessage());
            log.warn("Failed to ingest analytics event {}", event.getId(), ex);
        }
        analyticsEventRepository.save(event);
    }

    private void ingestLeaveEvent(AnalyticsEvent event) {
        Map<String, Object> payload = parsePayload(event);
        UUID leaveRequestId = uuid(payload.get("leaveRequestId"));
        LeaveFact fact = leaveFactRepository.findByLeaveRequestId(leaveRequestId)
            .orElseGet(() -> LeaveFact.builder().leaveRequestId(leaveRequestId).build());

        fact.setEventDate(localDate(payload.get("submittedAt"), event.getEventDate()));
        fact.setEmployeeId(uuid(payload.get("employeeId")));
        fact.setDepartmentId(uuidNullable(payload.get("departmentId")));
        fact.setProjectId(uuidNullable(payload.get("projectId")));
        fact.setTeamId(uuidNullable(payload.get("teamId")));
        fact.setLeaveTypeId(uuid(payload.get("leaveTypeId")));
        fact.setWorkingDays(integer(payload.get("workingDays")));
        fact.setRequestStatus(LeaveStatus.valueOf((String) payload.get("leaveStatus")));
        fact.setApprovalDurationDays(computeApprovalDurationDays(payload));

        leaveFactRepository.save(fact);
    }

    private void ingestApprovalEvent(AnalyticsEvent event) {
        Map<String, Object> payload = parsePayload(event);
        UUID stepId = uuid(payload.get("approvalStepId"));
        ApprovalFact fact = approvalFactRepository.findByApprovalStepId(stepId)
            .orElseGet(() -> ApprovalFact.builder().approvalStepId(stepId).build());

        fact.setEventDate(event.getEventDate());
        fact.setWorkflowId(uuid(payload.get("workflowId")));
        fact.setApproverId(uuid(payload.get("approverId")));
        fact.setSubjectType((String) payload.get("subjectType"));
        fact.setSourceType(ApprovalSourceType.valueOf((String) payload.get("sourceType")));
        fact.setApproverLevel(integer(payload.get("approverLevel")));
        fact.setStepStatus(StepStatus.valueOf((String) payload.get("stepStatus")));
        fact.setDecisionDelayDays(integer(payload.get("decisionDelayDays")));

        approvalFactRepository.save(fact);
    }

    private int computeApprovalDurationDays(Map<String, Object> payload) {
        Object submittedAtValue = payload.get("submittedAt");
        Object completedAtValue = payload.get("completedAt");
        if (submittedAtValue == null || completedAtValue == null) {
            return 0;
        }
        Instant submittedAt = Instant.parse((String) submittedAtValue);
        Instant completedAt = Instant.parse((String) completedAtValue);
        if (completedAt.isBefore(submittedAt)) {
            return 0;
        }
        return (int) Math.max(1L, java.time.Duration.between(submittedAt, completedAt).toDays());
    }

    private Map<String, Object> parsePayload(AnalyticsEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse analytics event payload", e);
        }
    }

    private UUID uuid(Object value) {
        return UUID.fromString((String) value);
    }

    private UUID uuidNullable(Object value) {
        return value == null ? null : UUID.fromString((String) value);
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private LocalDate localDate(Object value, LocalDate fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        if (text.contains("T")) {
            return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(text);
    }
}
