package com.hris.approval.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectAssignmentHierarchyResolver {

    private static final String ASSIGNMENT_CHAIN_SOURCE = "ASSIGNMENT_CHAIN";
    private static final String PROJECT_MANAGER_DEFAULT_SOURCE = "PROJECT_MANAGER_DEFAULT";

    private final EmployeeRepository employeeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectRepository projectRepository;

    public List<ProjectApprover> resolveApprovers(
            ProjectAssignment assignment,
            Employee requester,
            LocalDate startDate,
            LocalDate endDate) {
        List<ProjectApprover> approvers = new ArrayList<>();
        Set<UUID> visitedSupervisorEmployeeIds = new HashSet<>();

        UUID currentSupervisorId = assignment.getSupervisorId();
        UUID preferredTeamId = assignment.getTeamId();
        int distance = 1;
        String source = ASSIGNMENT_CHAIN_SOURCE;

        while (currentSupervisorId != null
            && !currentSupervisorId.equals(requester.getId())
            && visitedSupervisorEmployeeIds.add(currentSupervisorId)) {
            Employee supervisor = employeeRepository.findById(currentSupervisorId).orElse(null);
            if (supervisor == null) {
                break;
            }

            if (supervisor.getStatus() == EmployeeStatus.ACTIVE && supervisor.getUserId() != null) {
                approvers.add(new ProjectApprover(
                    supervisor.getUserId(),
                    distance,
                    "PROJECT_N_PLUS_" + distance,
                    source
                ));
            }

            Optional<ProjectAssignment> supervisorAssignment = selectUpstreamAssignment(
                currentSupervisorId,
                assignment.getProjectId(),
                preferredTeamId,
                startDate,
                endDate
            );

            UUID nextSupervisorId;
            if (supervisorAssignment.isPresent()) {
                ProjectAssignment upstreamAssignment = supervisorAssignment.get();
                preferredTeamId = upstreamAssignment.getTeamId();
                if (upstreamAssignment.getSupervisorId() != null
                    && !upstreamAssignment.getSupervisorId().equals(supervisor.getId())) {
                    nextSupervisorId = upstreamAssignment.getSupervisorId();
                    source = ASSIGNMENT_CHAIN_SOURCE;
                } else {
                    nextSupervisorId = resolveProjectManagerFallback(
                        assignment.getProjectId(), supervisor.getId(), requester.getId());
                    source = nextSupervisorId == null
                        ? ASSIGNMENT_CHAIN_SOURCE
                        : PROJECT_MANAGER_DEFAULT_SOURCE;
                }
            } else {
                nextSupervisorId = resolveProjectManagerFallback(
                    assignment.getProjectId(), supervisor.getId(), requester.getId());
                source = nextSupervisorId == null
                    ? ASSIGNMENT_CHAIN_SOURCE
                    : PROJECT_MANAGER_DEFAULT_SOURCE;
            }

            if (nextSupervisorId == null || nextSupervisorId.equals(currentSupervisorId)) {
                break;
            }

            currentSupervisorId = nextSupervisorId;
            distance++;
        }

        return approvers;
    }

    private Optional<ProjectAssignment> selectUpstreamAssignment(
            UUID employeeId,
            UUID projectId,
            UUID preferredTeamId,
            LocalDate startDate,
            LocalDate endDate) {
        List<ProjectAssignment> assignments = projectAssignmentRepository
            .findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(employeeId, projectId, startDate, endDate);
        if (assignments.isEmpty()) {
            return Optional.empty();
        }
        if (preferredTeamId != null) {
            Optional<ProjectAssignment> sameTeamAssignment = assignments.stream()
                .filter(candidate -> preferredTeamId.equals(candidate.getTeamId()))
                .findFirst();
            if (sameTeamAssignment.isPresent()) {
                return sameTeamAssignment;
            }
        }

        Optional<ProjectAssignment> projectLevelAssignment = assignments.stream()
            .filter(candidate -> candidate.getTeamId() == null)
            .findFirst();
        if (projectLevelAssignment.isPresent()) {
            return projectLevelAssignment;
        }

        Optional<ProjectAssignment> managerAssignment = assignments.stream()
            .filter(candidate -> candidate.getAssignmentRole() == ProjectRole.MANAGER)
            .findFirst();
        if (managerAssignment.isPresent()) {
            return managerAssignment;
        }

        return Optional.of(assignments.getFirst());
    }

    private UUID resolveProjectManagerFallback(UUID projectId, UUID currentEmployeeId, UUID requesterId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getProjectManagerEmployeeId() == null) {
            return null;
        }

        UUID projectManagerEmployeeId = project.getProjectManagerEmployeeId();
        if (projectManagerEmployeeId.equals(currentEmployeeId) || projectManagerEmployeeId.equals(requesterId)) {
            return null;
        }

        return projectManagerEmployeeId;
    }

    public record ProjectApprover(UUID approverUserId, int distance, String roleCode, String source) {
    }
}
