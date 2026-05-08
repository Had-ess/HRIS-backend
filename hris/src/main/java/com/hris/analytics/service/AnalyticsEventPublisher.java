package com.hris.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.entity.AnalyticsEvent;
import com.hris.analytics.enums.AnalyticsEventType;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.auth.entity.Employee;
import com.hris.leave.entity.LeaveRequest;
import com.hris.organisation.entity.ProjectAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsEventPublisher {

    private final TransactionalAnalyticsEventPublisher transactionalPublisher;
    private final ObjectMapper objectMapper;

    public void publishLeaveEvent(
            AnalyticsEventType eventType,
            LeaveRequest request,
            Employee employee,
            ProjectAssignment primaryAssignment,
            Instant completedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leaveRequestId", request.getId());
        payload.put("employeeId", request.getEmployeeId());
        payload.put("departmentId", employee.getDepartmentId());
        payload.put("projectId", primaryAssignment != null ? primaryAssignment.getProjectId() : null);
        payload.put("teamId", primaryAssignment != null ? primaryAssignment.getTeamId() : null);
        payload.put("leaveTypeId", request.getLeaveTypeId());
        payload.put("workingDays", request.getWorkingDays());
        payload.put("leaveStatus", request.getStatus().name());
        payload.put("submittedAt", request.getSubmittedAt() != null ? request.getSubmittedAt().toString() : null);
        payload.put("completedAt", completedAt != null ? completedAt.toString() : null);

        publish(buildEvent(
            eventType,
            "LEAVE_REQUEST",
            request.getId(),
            request.getSubmittedAt() != null ? request.getSubmittedAt() : Instant.now(),
            request.getStartDate(),
            payload
        ));
    }

    public void publishApprovalEvent(
            AnalyticsEventType eventType,
            ApprovalStep step,
            ApprovalWorkflow workflow) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", workflow.getId());
        payload.put("approvalStepId", step.getId());
        payload.put("approverId", step.getApproverId());
        payload.put("subjectType", workflow.getSubjectType());
        payload.put("sourceType", step.getSourceType() != null ? step.getSourceType().name() : "PRIMARY_CHAIN");
        payload.put("approverLevel", step.getApproverLevel() != null ? step.getApproverLevel() : 1);
        payload.put("stepStatus", step.getStatus().name());
        payload.put("workflowCreatedAt", workflow.getCreatedAt() != null ? workflow.getCreatedAt().toString() : null);
        payload.put("decidedAt", step.getDecidedAt() != null ? step.getDecidedAt().toString() : null);
        payload.put("decisionDelayDays", computeDelayDays(workflow.getCreatedAt(), step.getDecidedAt()));

        publish(buildEvent(
            eventType,
            "APPROVAL_STEP",
            step.getId(),
            step.getDecidedAt() != null ? step.getDecidedAt() : Instant.now(),
            step.getDecidedAt() != null ? step.getDecidedAt().atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now(ZoneOffset.UTC),
            payload
        ));
    }

    public void publishEmployeeHireEvent(Employee employee) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employee.getId());
        payload.put("departmentId", employee.getDepartmentId());
        payload.put("employeeStatus", employee.getStatus().name());
        payload.put("hireDate", employee.getHireDate() != null ? employee.getHireDate().toString() : null);
        publish(buildEvent(AnalyticsEventType.EMPLOYEE_HIRED, "EMPLOYEE", employee.getId(), Instant.now(), employee.getHireDate(), payload));
    }

    public void publishEmployeeTransferEvent(Employee previous, Employee current) {
        if (previous.getDepartmentId() == null || previous.getDepartmentId().equals(current.getDepartmentId())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", current.getId());
        payload.put("previousDepartmentId", previous.getDepartmentId());
        payload.put("newDepartmentId", current.getDepartmentId());
        payload.put("employeeStatus", current.getStatus().name());
        publish(buildEvent(AnalyticsEventType.EMPLOYEE_TRANSFERRED, "EMPLOYEE", current.getId(), Instant.now(), LocalDate.now(ZoneOffset.UTC), payload));
    }

    public void publishEmployeeTerminationEvent(Employee employee) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employee.getId());
        payload.put("departmentId", employee.getDepartmentId());
        payload.put("employeeStatus", employee.getStatus().name());
        payload.put("terminationDate", employee.getTerminationDate() != null ? employee.getTerminationDate().toString() : null);
        publish(buildEvent(
            AnalyticsEventType.EMPLOYEE_TERMINATED,
            "EMPLOYEE",
            employee.getId(),
            Instant.now(),
            employee.getTerminationDate() != null ? employee.getTerminationDate() : LocalDate.now(ZoneOffset.UTC),
            payload
        ));
    }

    public void publishProjectAssignmentEvent(AnalyticsEventType eventType, ProjectAssignment assignment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectAssignmentId", assignment.getId());
        payload.put("employeeId", assignment.getEmployeeId());
        payload.put("projectId", assignment.getProjectId());
        payload.put("teamId", assignment.getTeamId());
        payload.put("startDate", assignment.getStartDate() != null ? assignment.getStartDate().toString() : null);
        payload.put("endDate", assignment.getEndDate() != null ? assignment.getEndDate().toString() : null);
        publish(buildEvent(
            eventType,
            "PROJECT_ASSIGNMENT",
            assignment.getId(),
            Instant.now(),
            assignment.getStartDate() != null ? assignment.getStartDate() : LocalDate.now(ZoneOffset.UTC),
            payload
        ));
    }

    private void publish(AnalyticsEvent event) {
        transactionalPublisher.publishAfterCommit(event);
    }

    private AnalyticsEvent buildEvent(
            AnalyticsEventType eventType,
            String aggregateType,
            UUID aggregateId,
            Instant occurredAt,
            LocalDate eventDate,
            Map<String, Object> payload) {
        return AnalyticsEvent.builder()
            .eventType(eventType)
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .occurredAt(occurredAt)
            .eventDate(eventDate != null ? eventDate : LocalDate.now(ZoneOffset.UTC))
            .payload(serialize(payload))
            .build();
    }

    private int computeDelayDays(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return (int) Math.max(1L, Duration.between(start, end).toDays());
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize analytics payload", e);
        }
    }
}
