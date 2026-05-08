package com.hris.approval.service;

import com.hris.approval.enums.ApprovalContext;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
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
    private final EmployeeHierarchyResolver employeeHierarchyResolver;
    private final ProjectAssignmentHierarchyResolver projectAssignmentHierarchyResolver;

    public ApprovalRoutePlan resolveRoutePlan(UUID requesterId, LocalDate startDate, LocalDate endDate) {
        Employee employee = employeeRepository.findById(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + requesterId));

        List<ProjectAssignment> activeAssignments =
            projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, startDate, endDate);

        List<ApprovalRouteStep> steps = new ArrayList<>();
        Set<UUID> addedApproverUsers = new HashSet<>();
        int order = addEmployeeHierarchyStep(steps, addedApproverUsers, 1, employee);
        addAssignmentHierarchySteps(activeAssignments, employee, startDate, endDate, steps, addedApproverUsers, order);

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
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            List<ApprovalRouteStep> steps,
                                            Set<UUID> addedApproverUsers,
                                            int order) {
        for (ProjectAssignment assignment : assignments) {
            List<ProjectAssignmentHierarchyResolver.ProjectApprover> projectApprovers =
                projectAssignmentHierarchyResolver.resolveApprovers(
                    assignment, requester, startDate, endDate);
            for (ProjectAssignmentHierarchyResolver.ProjectApprover projectApprover : projectApprovers) {
                if (!addedApproverUsers.add(projectApprover.approverUserId())) {
                    continue;
                }

                Map<String, String> snapshot = new LinkedHashMap<>();
                snapshot.put("projectId", assignment.getProjectId().toString());
                snapshot.put("role", projectApprover.roleCode());
                snapshot.put("distance", Integer.toString(projectApprover.distance()));
                snapshot.put("source", projectApprover.source());
                if (assignment.getTeamId() != null) {
                    snapshot.put("teamId", assignment.getTeamId().toString());
                }

                steps.add(new ApprovalRouteStep(
                    projectApprover.approverUserId(),
                    order++,
                    ApprovalContext.PROJECT,
                    snapshot
                ));
            }
        }

        return order;
    }

    public record ApprovalRoutePlan(List<ApprovalRouteStep> steps) {
    }

    public record ApprovalRouteStep(UUID approverId,
                                    int stepOrder,
                                    ApprovalContext context,
                                    Map<String, String> routingSnapshot) {
    }
}
