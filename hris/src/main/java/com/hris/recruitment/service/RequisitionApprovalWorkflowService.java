package com.hris.recruitment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.settings.validation.entity.ValidationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hris.recruitment.entity.Requisition;

/**
 * Instantiates the multi-level approval workflow for a requisition on the <em>existing</em>
 * approval engine, so requisition approvals show up in the same inbox and use the same
 * approve/reject mechanics as leave.
 *
 * <p>Approver resolution walks outward from the hiring manager: their department head &rarr;
 * parent-department head(s) up the {@code parent_department_id} chain (director level) &rarr;
 * HR fallback (holders of {@code RECRUITMENT_APPROVE}). Approvers are de-duplicated and the
 * hiring manager is always excluded. The workflow is {@code ALL_REQUIRED}.
 */
@Service
@RequiredArgsConstructor
public class RequisitionApprovalWorkflowService {

    static final String SUBJECT_TYPE = "REQUISITION";
    static final String FALLBACK_PERMISSION = "RECRUITMENT_APPROVE";

    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApprovalWorkflow instantiate(Requisition requisition, Employee hiringManager) {
        List<RouteApprover> approvers = resolveApprovers(requisition, hiringManager);
        if (approvers.isEmpty()) {
            throw new InvalidWorkflowStateException(
                "No approver could be resolved for this requisition");
        }

        // ONE_REQUIRED: the requisition is routed to the resolved chain (hiring manager's
        // department head -> parent/director -> HR fallback pool), and the first of them to
        // approve opens it (the engine auto-skips the rest). ALL_REQUIRED would wrongly demand
        // every HR fallback approver act; per-step ALL-of escalation is a later enhancement.
        ApprovalWorkflow workflow = approvalWorkflowRepository.save(ApprovalWorkflow.builder()
            .subjectType(SUBJECT_TYPE)
            .subjectId(requisition.getId())
            .status(WorkflowStatus.IN_PROGRESS)
            .workflowCode("REQUISITION_APPROVAL")
            .validationMode(ValidationMode.ONE_REQUIRED)
            .requiredApprovals(1)
            .routingSnapshot(serialize(buildWorkflowSnapshot(requisition, hiringManager, approvers)))
            .createdAt(Instant.now())
            .build());

        approvalStepRepository.saveAll(buildSteps(workflow.getId(), approvers));
        return workflow;
    }

    private List<RouteApprover> resolveApprovers(Requisition requisition, Employee hiringManager) {
        UUID hmUserId = hiringManager.getUserId();
        Map<UUID, RouteApprover> byUser = new LinkedHashMap<>();
        int[] level = {1};

        // 1. Department head of the requisition's department.
        Department department = departmentRepository.findById(requisition.getDepartmentId()).orElse(null);
        if (department != null && department.getHeadEmployeeId() != null
            && !department.getHeadEmployeeId().equals(hiringManager.getId())) {
            addEmployeeApprover(byUser, department.getHeadEmployeeId(), hmUserId,
                "DEPT_HEAD", ApprovalSourceType.PROFILE_BASED, level);
        }

        // 2. Walk the parent-department chain (director level).
        Set<UUID> visited = new HashSet<>();
        UUID cursor = department == null ? null : department.getParentDepartmentId();
        while (cursor != null && visited.add(cursor)) {
            Department parent = departmentRepository.findById(cursor).orElse(null);
            if (parent == null) {
                break;
            }
            if (parent.getHeadEmployeeId() != null
                && !parent.getHeadEmployeeId().equals(hiringManager.getId())) {
                addEmployeeApprover(byUser, parent.getHeadEmployeeId(), hmUserId,
                    "PARENT_DEPT_HEAD", ApprovalSourceType.PROFILE_BASED, level);
            }
            cursor = parent.getParentDepartmentId();
        }

        // 3. HR fallback — holders of RECRUITMENT_APPROVE.
        if (byUser.isEmpty()) {
            for (User user : userRepository.findByPermissionNames(List.of(FALLBACK_PERMISSION))) {
                if (user == null || !user.isActive() || user.getId().equals(hmUserId)) {
                    continue;
                }
                byUser.putIfAbsent(user.getId(), new RouteApprover(
                    null, user.getId(), level[0]++, "HR_FALLBACK", ApprovalSourceType.FALLBACK));
            }
        }

        return new ArrayList<>(byUser.values());
    }

    private void addEmployeeApprover(Map<UUID, RouteApprover> byUser, UUID employeeId, UUID excludeUserId,
                                     String role, ApprovalSourceType sourceType, int[] level) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getUserId() == null || employee.getUserId().equals(excludeUserId)) {
            return;
        }
        User user = userRepository.findById(employee.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }
        byUser.putIfAbsent(employee.getUserId(),
            new RouteApprover(employee.getId(), employee.getUserId(), level[0]++, role, sourceType));
    }

    private List<ApprovalStep> buildSteps(UUID workflowId, List<RouteApprover> approvers) {
        List<ApprovalStep> steps = new ArrayList<>();
        int order = 1;
        for (RouteApprover approver : approvers) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("role", approver.role());
            snapshot.put("approverEmployeeId", approver.employeeId());
            snapshot.put("approverUserId", approver.userId());
            snapshot.put("level", approver.level());

            steps.add(ApprovalStep.builder()
                .workflowId(workflowId)
                .approverId(approver.userId())
                .stepOrder(order++)
                .status(StepStatus.PENDING)
                .context(ApprovalContext.TEAM)
                .sourceType(approver.sourceType())
                .approverLevel(approver.level())
                .routingSnapshot(serialize(snapshot))
                .build());
        }
        return steps;
    }

    private Map<String, Object> buildWorkflowSnapshot(Requisition requisition, Employee hiringManager,
                                                      List<RouteApprover> approvers) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("workflowCode", "REQUISITION_APPROVAL");
        snapshot.put("requisitionId", requisition.getId());
        snapshot.put("requisitionTitle", requisition.getTitle());
        snapshot.put("hiringManagerEmployeeId", hiringManager.getId());
        snapshot.put("hiringManagerUserId", hiringManager.getUserId());
        snapshot.put("resolvedApprovers", approvers.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", a.employeeId());
            m.put("userId", a.userId());
            m.put("level", a.level());
            m.put("role", a.role());
            m.put("sourceType", a.sourceType().name());
            return m;
        }).toList());
        return snapshot;
    }

    private String serialize(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize requisition routing snapshot", ex);
        }
    }

    private record RouteApprover(
        UUID employeeId,
        UUID userId,
        int level,
        String role,
        ApprovalSourceType sourceType
    ) {}
}
