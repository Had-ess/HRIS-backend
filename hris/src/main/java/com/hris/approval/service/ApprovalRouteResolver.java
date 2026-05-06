package com.hris.approval.service;

import com.hris.approval.enums.ApprovalContext;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalRouteResolver {

    private final EmployeeRepository employeeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeHierarchyResolver employeeHierarchyResolver;

    public ApprovalRoutePlan resolveRoutePlan(UUID requesterId, LocalDate startDate, LocalDate endDate) {
        Employee employee = employeeRepository.findById(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + requesterId));

        List<ProjectAssignment> activeAssignments =
            projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, startDate, endDate);

        List<ApprovalRouteStep> steps = new ArrayList<>();
        Set<UUID> addedApproverUsers = new HashSet<>();
        int order = addEmployeeHierarchyStep(steps, addedApproverUsers, 1, employee);
        addAssignmentHierarchySteps(activeAssignments, employee, steps, addedApproverUsers, order);

        if (steps.isEmpty()) {
            throw new MissingDepartmentHeadException("No approval supervisor could be resolved");
        }

        return new ApprovalRoutePlan(steps);
    }

    private int addEmployeeHierarchyStep(List<ApprovalRouteStep> steps,
                                         Set<UUID> addedApproverUsers,
                                         int order,
                                         Employee requester) {
        Optional<EmployeeHierarchyResolver.HierarchyApprover> hierarchyApprover =
            employeeHierarchyResolver.resolveNextApprover(requester);
        if (hierarchyApprover.isEmpty()) {
            return order;
        }

        Employee approver = hierarchyApprover.get().employee();
        if (!addedApproverUsers.add(approver.getUserId())) {
            return order;
        }

        Map<String, String> snapshot = new LinkedHashMap<>();
        if (requester.getDepartmentId() != null) {
            snapshot.put("departmentId", requester.getDepartmentId().toString());
        }
        snapshot.put("role", hierarchyApprover.get().roleCode());
        snapshot.put("distance", Integer.toString(hierarchyApprover.get().distance()));

        steps.add(new ApprovalRouteStep(
            approver.getUserId(),
            order,
            ApprovalContext.DEPARTMENT,
            snapshot
        ));
        return order + 1;
    }

    private int addAssignmentHierarchySteps(List<ProjectAssignment> assignments,
                                            Employee requester,
                                            List<ApprovalRouteStep> steps,
                                            Set<UUID> addedApproverUsers,
                                            int order) {
        for (ProjectAssignment assignment : assignments) {
            ResolvedApprover resolvedApprover = resolveApproverForAssignment(assignment, requester);
            if (resolvedApprover == null || !addedApproverUsers.add(resolvedApprover.userId())) {
                continue;
            }

            Map<String, String> snapshot = new LinkedHashMap<>();
            snapshot.put("projectId", assignment.getProjectId().toString());
            snapshot.put("role", resolvedApprover.roleCode());
            if (assignment.getTeamId() != null) {
                snapshot.put("teamId", assignment.getTeamId().toString());
            }

            steps.add(new ApprovalRouteStep(
                resolvedApprover.userId(),
                order++,
                resolvedApprover.context(),
                snapshot
            ));
        }

        return order;
    }

    private ResolvedApprover resolveApproverForAssignment(ProjectAssignment assignment, Employee requester) {
        if (assignment.getTeamId() != null) {
            ResolvedApprover teamLeader = resolveEmployeeApprover(
                assignment.getSupervisorId(),
                requester,
                ApprovalContext.PROJECT,
                "TEAM_LEADER"
            );
            if (teamLeader != null) {
                return teamLeader;
            }
        }

        Project project = projectRepository.findById(assignment.getProjectId())
            .orElseThrow(() -> new EntityNotFoundException("Project not found: " + assignment.getProjectId()));
        ResolvedApprover projectManager = resolveEmployeeApprover(
            project.getProjectManagerEmployeeId(),
            requester,
            ApprovalContext.PROJECT,
            "PROJECT_MANAGER"
        );
        if (projectManager != null) {
            return projectManager;
        }

        return null;
    }

    private ResolvedApprover resolveEmployeeApprover(UUID employeeId,
                                                     Employee requester,
                                                     ApprovalContext context,
                                                     String roleCode) {
        if (employeeId == null || employeeId.equals(requester.getId())) {
            return null;
        }

        Employee approverEmployee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Approver employee not found: " + employeeId));
        if (approverEmployee.getStatus() != com.hris.auth.enums.EmployeeStatus.ACTIVE) {
            return null;
        }

        return new ResolvedApprover(approverEmployee.getUserId(), context, roleCode);
    }

    public record ApprovalRoutePlan(List<ApprovalRouteStep> steps) {
    }

    public record ApprovalRouteStep(UUID approverId,
                                    int stepOrder,
                                    ApprovalContext context,
                                    Map<String, String> routingSnapshot) {
    }

    private record ResolvedApprover(UUID userId, ApprovalContext context, String roleCode) {
    }
}
