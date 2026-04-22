package com.hris.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRouter {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public List<ApprovalStep> resolveSteps(UUID requesterId, UUID workflowId,
                                            LocalDate startDate, LocalDate endDate) {
        Employee employee = employeeRepository.findById(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + requesterId));

        List<ProjectAssignment> activeAssignments =
            projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, startDate, endDate);

        List<ApprovalStep> steps = !activeAssignments.isEmpty()
            ? buildProjectSupervisorSteps(activeAssignments, requesterId, workflowId)
            : buildDepartmentHeadFallbackStep(employee, requesterId, workflowId);

        if (steps.isEmpty()) {
            throw new InvalidWorkflowStateException("No approvers could be resolved for this workflow");
        }

        return approvalStepRepository.saveAll(steps);
    }

    private List<ApprovalStep> buildProjectSupervisorSteps(List<ProjectAssignment> assignments,
                                                           UUID requesterId,
                                                           UUID workflowId) {
        List<ApprovalStep> steps = new ArrayList<>();
        Set<UUID> addedSupervisors = new HashSet<>();
        int order = 1;

        for (ProjectAssignment assignment : assignments) {
            UUID supervisorId = assignment.getSupervisorId();
            if (supervisorId == null
                || supervisorId.equals(requesterId)
                || !addedSupervisors.add(supervisorId)) {
                continue;
            }

            Employee supervisor = employeeRepository.findById(supervisorId)
                .orElseThrow(() -> new EntityNotFoundException("Supervisor not found: " + supervisorId));

            steps.add(buildStep(workflowId, supervisor.getUserId(), order++,
                ApprovalContext.PROJECT,
                Map.of(
                    "projectId", assignment.getProjectId().toString(),
                    "role", "PROJECT_SUPERVISOR"
                )));
        }

        return steps;
    }

    private List<ApprovalStep> buildDepartmentHeadFallbackStep(Employee employee,
                                                               UUID requesterId,
                                                               UUID workflowId) {
        if (employee.getDepartmentId() == null) {
            return List.of(buildAdministrationFallbackStep(workflowId, requesterId, Map.of(
                "role", "ADMINISTRATION_FALLBACK"
            )));
        }

        Employee headEmployee = departmentRepository.findDepartmentHead(employee.getDepartmentId()).orElse(null);

        if (headEmployee == null || headEmployee.getId().equals(requesterId)) {
            return List.of(buildAdministrationFallbackStep(workflowId, requesterId, Map.of(
                "departmentId", employee.getDepartmentId().toString(),
                "role", "ADMINISTRATION_FALLBACK"
            )));
        }

        return List.of(buildStep(workflowId, headEmployee.getUserId(), 1,
            ApprovalContext.DEPARTMENT,
            Map.of(
                "departmentId", employee.getDepartmentId().toString(),
                "role", "DEPT_HEAD"
            )));
    }

    private ApprovalStep buildAdministrationFallbackStep(UUID workflowId,
                                                         UUID requesterId,
                                                         Map<String, String> snapshot) {
        User approver = findFallbackApprover(requesterId, "ADMINISTRATION")
            .or(() -> findFallbackApprover(requesterId, "DIRECTOR"))
            .or(() -> findFallbackApprover(requesterId, "HR_ADMIN"))
            .orElseThrow(() -> new MissingDepartmentHeadException(
                "No department head defined for fallback approval"));

        return buildStep(workflowId, approver.getId(), 1, ApprovalContext.DEPARTMENT, snapshot);
    }

    private java.util.Optional<User> findFallbackApprover(UUID requesterId, String roleCode) {
        return userRepository.findByRole(roleCode).stream()
            .filter(User::isActive)
            .filter(user -> !user.getId().equals(requesterId))
            .findFirst();
    }

    private ApprovalStep buildStep(UUID workflowId, UUID approverId, int order,
                                    ApprovalContext context,
                                    Map<String, String> snapshot) {
        return ApprovalStep.builder()
            .workflowId(workflowId)
            .approverId(approverId)
            .stepOrder(order)
            .status(StepStatus.PENDING)
            .context(context)
            .routingSnapshot(serializeSnapshot(snapshot))
            .build();
    }

    private String serializeSnapshot(Map<String, String> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize routing snapshot", e);
            return snapshot.toString();
        }
    }
}
