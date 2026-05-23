package com.hris.leave.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.TeamHierarchyResolver;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.TeamProjectLink;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.TeamProjectLinkRepository;
import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.entity.ValidatorSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaveApprovalWorkflowService {

    private static final String FALLBACK_PERMISSION = "LEAVE_REQUEST_FALLBACK_APPROVE";
    private static final String DEPT_APPROVER_PROFILE_CODE = "DEPT_APPROVER_PROFILE";

    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AccessProfileRepository accessProfileRepository;
    private final TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TeamProjectLinkRepository teamProjectLinkRepository;
    private final TeamHierarchyResolver teamHierarchyResolver;
    private final LeaveValidationWorkflowResolver leaveValidationWorkflowResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public InstantiatedWorkflow instantiate(LeaveRequest request, Employee requesterEmployee, LeaveType leaveType) {
        ValidationWorkflow validationWorkflow = leaveValidationWorkflowResolver.resolveForLeaveType(leaveType);
        UUID teamId = resolveTeamId(requesterEmployee.getId(), request.getStartDate(), request.getEndDate());

        RouteResolution routeResolution = resolveRoute(requesterEmployee, teamId, request.getStartDate(), validationWorkflow);
        List<RouteApprover> approvers = routeResolution.approvers();
        if (approvers.isEmpty()) {
            throw new InvalidWorkflowStateException("No approvers could be resolved for this leave request");
        }

        int requiredApprovals = resolveRequiredApprovals(validationWorkflow, approvers);
        ApprovalWorkflow workflow = approvalWorkflowRepository.save(ApprovalWorkflow.builder()
            .subjectType("LEAVE")
            .subjectId(request.getId())
            .status(WorkflowStatus.IN_PROGRESS)
            .workflowCode(validationWorkflow.getCode())
            .validationMode(validationWorkflow.getValidationMode())
            .requiredApprovals(requiredApprovals)
            .routingSnapshot(serialize(buildWorkflowSnapshot(request, requesterEmployee, leaveType, teamId, validationWorkflow, routeResolution)))
            .createdAt(Instant.now())
            .build());

        List<ApprovalStep> steps = approvalStepRepository.saveAll(buildSteps(workflow.getId(), validationWorkflow, routeResolution));
        return new InstantiatedWorkflow(workflow, steps);
    }

    private UUID resolveTeamId(UUID requesterEmployeeId, LocalDate startDate, LocalDate endDate) {
        UUID teamIdFromAssignment = projectAssignmentRepository.findActiveAssignmentsDuringPeriod(requesterEmployeeId, startDate, endDate).stream()
            .map(ProjectAssignment::getTeamId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (teamIdFromAssignment != null) {
            return teamIdFromAssignment;
        }

        // Fallback for data gaps: derive team from active hierarchy membership, but
        // only keep teams effectively linked to at least one active project.
        List<TeamHierarchyRelation> activeMemberships =
            teamHierarchyRelationRepository.findByCollaboratorEmployeeIdAndStatusOrderByStartDateAscTeamIdAsc(
                requesterEmployeeId,
                TeamHierarchyStatus.ACTIVE);

        List<UUID> membershipTeamIds = activeMemberships.stream()
            .filter(relation -> isRelationEffectiveDuring(relation, startDate, endDate))
            .map(TeamHierarchyRelation::getTeamId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (membershipTeamIds.isEmpty()) {
            return null;
        }

        Set<UUID> activeLinkedTeamIds = new LinkedHashSet<>(
            teamProjectLinkRepository.findActiveTeamIdsDuringPeriod(membershipTeamIds, startDate, endDate)
        );

        if (activeLinkedTeamIds.isEmpty()) {
            return null;
        }

        return membershipTeamIds.stream()
            .filter(activeLinkedTeamIds::contains)
            .findFirst()
            .orElse(null);
    }

    private RouteResolution resolveRoute(Employee requesterEmployee, UUID teamId, LocalDate effectiveDate, ValidationWorkflow workflow) {
        List<RouteApprover> approvers = new ArrayList<>();
        boolean fallbackUsed = false;
        String fallbackReason = null;
        ValidatorSource source = workflow.getValidatorSource();

        boolean useTeamHierarchy = source == ValidatorSource.TEAM_HIERARCHY
            || source == ValidatorSource.HYBRID
            || source == null;

        if (useTeamHierarchy && teamId != null) {
            TeamHierarchyResolver.RouteCandidateList candidateList =
                teamHierarchyResolver.resolveAboveRequester(teamId, requesterEmployee.getId(), effectiveDate);
            approvers.addAll(toHierarchyApprovers(candidateList, requesterEmployee.getId()));
            if (candidateList.noValidator() || approvers.isEmpty()) {
                fallbackUsed = true;
                fallbackReason = "NO_HIERARCHY_VALIDATOR";
            }
        } else if (useTeamHierarchy) {
            fallbackUsed = true;
            fallbackReason = "NO_TEAM";
        }

        boolean teamHead = useTeamHierarchy && approvers.isEmpty()
            && isTeamChainHead(requesterEmployee.getId(), teamId, effectiveDate);
        boolean deptHead = approvers.isEmpty() && isDeptHead(requesterEmployee.getId());

        boolean shouldEscalateViaProfiles = approvers.isEmpty() && (
            source == ValidatorSource.PROFILE_BASED
                || source == ValidatorSource.HYBRID
                || teamHead
                || deptHead
        );

        if (shouldEscalateViaProfiles) {
            List<RouteApprover> profileApprovers = resolveProfileBasedEscalation(
                requesterEmployee, teamHead, deptHead);
            if (!profileApprovers.isEmpty()) {
                approvers.addAll(profileApprovers);
                if (fallbackReason == null) {
                    fallbackReason = source == ValidatorSource.PROFILE_BASED
                        ? "PROFILE_BASED_PRIMARY"
                        : "TEAM_HEAD_CEILING_ESCALATION";
                }
            }
        }

        if (approvers.isEmpty()) {
            approvers = resolveFallbackApprovers(workflow, requesterEmployee.getUserId());
        }

        List<RouteApprover> distinctApprovers = approvers.stream()
            .filter(approver -> !approver.userId().equals(requesterEmployee.getUserId()))
            .collect(
                LinkedHashMap<UUID, RouteApprover>::new,
                (map, approver) -> map.putIfAbsent(approver.userId(), approver),
                Map::putAll
            )
            .values()
            .stream()
            .toList();

        return new RouteResolution(distinctApprovers, fallbackUsed, fallbackReason);
    }

    private boolean isTeamChainHead(UUID requesterEmployeeId, UUID teamId, LocalDate effectiveDate) {
        if (teamId == null) {
            return false;
        }
        return teamHierarchyRelationRepository
            .findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE)
            .stream()
            .filter(relation -> isRelationEffectiveOn(relation, effectiveDate))
            .anyMatch(relation -> requesterEmployeeId.equals(relation.getCollaboratorEmployeeId())
                && relation.getResponsibleEmployeeId() == null);
    }

    private boolean isDeptHead(UUID requesterEmployeeId) {
        return departmentRepository.existsByHeadEmployeeId(requesterEmployeeId);
    }

    private boolean isRelationEffectiveOn(TeamHierarchyRelation relation, LocalDate date) {
        return !relation.getStartDate().isAfter(date)
            && (relation.getEndDate() == null || !relation.getEndDate().isBefore(date));
    }

    private boolean isRelationEffectiveDuring(TeamHierarchyRelation relation, LocalDate startDate, LocalDate endDate) {
        return !relation.getStartDate().isAfter(endDate)
            && (relation.getEndDate() == null || !relation.getEndDate().isBefore(startDate));
    }

    private List<RouteApprover> resolveProfileBasedEscalation(
            Employee requesterEmployee,
            boolean teamHead,
            boolean deptHead) {
        List<RouteApprover> approvers = new ArrayList<>();
        int level = 1;

        if (teamHead && requesterEmployee.getDepartmentId() != null && !deptHead) {
            Department department = departmentRepository.findById(requesterEmployee.getDepartmentId()).orElse(null);
            if (department != null
                && department.getHeadEmployeeId() != null
                && !department.getHeadEmployeeId().equals(requesterEmployee.getId())) {
                Employee head = employeeRepository.findById(department.getHeadEmployeeId()).orElse(null);
                if (head != null && head.getUserId() != null
                    && !head.getUserId().equals(requesterEmployee.getUserId())) {
                    approvers.add(new RouteApprover(
                        head.getId(),
                        head.getUserId(),
                        level++,
                        "PROFILE_BASED",
                        "DEPT_HEAD",
                        ApprovalSourceType.PROFILE_BASED,
                        false,
                        null
                    ));
                }
            }
        }

        if (approvers.isEmpty() || deptHead) {
            UUID profileId = accessProfileRepository.findByCodeIgnoreCase(DEPT_APPROVER_PROFILE_CODE)
                .map(profile -> profile.getId())
                .orElse(null);
            if (profileId != null) {
                List<User> deptApprovers = userRepository.findByAccessProfileId(profileId);
                for (User user : deptApprovers) {
                    if (user == null || !user.isActive() || requesterEmployee.getUserId().equals(user.getId())) {
                        continue;
                    }
                    approvers.add(new RouteApprover(
                        null,
                        user.getId(),
                        level++,
                        "PROFILE_BASED",
                        "DEPT_APPROVER_PROFILE",
                        ApprovalSourceType.PROFILE_BASED,
                        false,
                        null
                    ));
                }
            }
        }

        return approvers;
    }

    private List<RouteApprover> toHierarchyApprovers(TeamHierarchyResolver.RouteCandidateList candidateList, UUID requesterEmployeeId) {
        if (candidateList.candidates().isEmpty()) {
            return List.of();
        }

        Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                candidateList.candidates().stream().map(TeamHierarchyResolver.RouteCandidate::employeeId).toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(Employee::getId, employee -> employee));

        List<RouteApprover> approvers = new ArrayList<>();
        for (TeamHierarchyResolver.RouteCandidate candidate : candidateList.candidates()) {
            Employee employee = employeesById.get(candidate.employeeId());
            if (employee == null || employee.getUserId() == null) {
                continue;
            }
            approvers.add(new RouteApprover(
                employee.getId(),
                employee.getUserId(),
                candidate.level(),
                "TEAM_HIERARCHY",
                candidate.level() == 1 ? "DIRECT_RESPONSIBLE" : "CHAIN_LEVEL_" + candidate.level(),
                ApprovalSourceType.TEAM_CHAIN,
                false,
                candidate.directResponsibleEmployeeId()
            ));
        }
        return approvers.stream()
            .filter(approver -> !approver.userId().equals(requesterEmployeeId))
            .toList();
    }

    private List<RouteApprover> resolveFallbackApprovers(ValidationWorkflow workflow, UUID requesterUserId) {
        List<User> users = switch (workflow.getFallbackMode()) {
            case SPECIFIC_PROFILE -> userRepository.findByAccessProfileId(workflow.getFallbackProfileId());
            case SPECIFIC_PERMISSION -> userRepository.findByPermissionNames(List.of(workflow.getFallbackPermissionCode()));
            case HR_QUEUE -> userRepository.findByPermissionNames(List.of(FALLBACK_PERMISSION));
            case BLOCK_SUBMISSION -> List.of();
        };

        List<RouteApprover> approvers = new ArrayList<>();
        int level = 1;
        for (User user : users) {
            if (user == null || !user.isActive() || requesterUserId.equals(user.getId())) {
                continue;
            }
            approvers.add(new RouteApprover(
                null,
                user.getId(),
                level++,
                "FALLBACK",
                workflow.getFallbackMode().name(),
                ApprovalSourceType.FALLBACK,
                false,
                null
            ));
        }

        if (approvers.isEmpty()) {
            throw new InvalidWorkflowStateException(
                workflow.getFallbackMode() == ValidationFallbackMode.BLOCK_SUBMISSION
                    ? "No hierarchy approver could be resolved for this leave request"
                    : "Fallback approvers could not be resolved for this leave request");
        }

        return approvers;
    }

    private int resolveRequiredApprovals(ValidationWorkflow workflow, List<RouteApprover> approvers) {
        return switch (workflow.getValidationMode()) {
            case ONE_REQUIRED, INFO_PLUS_PRIMARY -> 1;
            case ALL_REQUIRED -> approvers.size();
            case MIN_N -> {
                int required = workflow.getMinValidators() == null ? 0 : workflow.getMinValidators();
                if (required < 1) {
                    throw new InvalidWorkflowStateException("minValidators must be configured for MIN_N workflows");
                }
                if (approvers.size() < required) {
                    throw new InvalidWorkflowStateException("Resolved approver count is lower than minValidators");
                }
                yield required;
            }
        };
    }

    private List<ApprovalStep> buildSteps(UUID workflowId, ValidationWorkflow workflow, RouteResolution routeResolution) {
        List<RouteApprover> approvers = routeResolution.approvers();
        List<ApprovalStep> steps = new ArrayList<>();
        int order = 1;
        for (RouteApprover approver : approvers) {
            boolean informational = workflow.getValidationMode() == ValidationMode.INFO_PLUS_PRIMARY && order > 1;
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("role", approver.roleCode());
            snapshot.put("source", approver.source());
            snapshot.put("approverEmployeeId", approver.employeeId());
            snapshot.put("approverUserId", approver.userId());
            snapshot.put("level", approver.level());
            snapshot.put("informational", informational);
            snapshot.put("fallbackUsed", routeResolution.fallbackUsed());
            snapshot.put("fallbackReason", routeResolution.fallbackReason());
            if (approver.directResponsibleEmployeeId() != null) {
                snapshot.put("directResponsibleEmployeeId", approver.directResponsibleEmployeeId());
            }

            steps.add(ApprovalStep.builder()
                .workflowId(workflowId)
                .approverId(approver.userId())
                .stepOrder(order)
                .status(informational ? StepStatus.INFORMATIONAL : StepStatus.PENDING)
                .context(ApprovalContext.TEAM)
                .sourceType(approver.sourceType())
                .approverLevel(approver.level())
                .routingSnapshot(serialize(snapshot))
                .build());
            order++;
        }
        return steps;
    }

    private Map<String, Object> buildWorkflowSnapshot(
            LeaveRequest request,
            Employee requesterEmployee,
            LeaveType leaveType,
            UUID teamId,
            ValidationWorkflow workflow,
            RouteResolution routeResolution) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("workflowCode", workflow.getCode());
        snapshot.put("validationMode", workflow.getValidationMode().name());
        snapshot.put("requesterEmployeeId", requesterEmployee.getId());
        snapshot.put("requesterUserId", requesterEmployee.getUserId());
        snapshot.put("leaveTypeId", leaveType.getId());
        snapshot.put("leaveTypeCode", leaveType.getCode());
        snapshot.put("leaveRequestId", request.getId());
        snapshot.put("teamId", teamId);
        snapshot.put("fallbackUsed", routeResolution.fallbackUsed());
        snapshot.put("fallbackReason", routeResolution.fallbackReason());
        snapshot.put("resolvedApprovers", routeResolution.approvers().stream().map(approver -> {
            Map<String, Object> approverSnapshot = new LinkedHashMap<>();
            approverSnapshot.put("employeeId", approver.employeeId());
            approverSnapshot.put("userId", approver.userId());
            approverSnapshot.put("level", approver.level());
            approverSnapshot.put("source", approver.source());
            approverSnapshot.put("role", approver.roleCode());
            approverSnapshot.put("sourceType", approver.sourceType().name());
            return approverSnapshot;
        }).toList());
        return snapshot;
    }

    private String serialize(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize approval routing snapshot", ex);
        }
    }

    public record InstantiatedWorkflow(ApprovalWorkflow workflow, List<ApprovalStep> steps) {
    }

    private record RouteResolution(List<RouteApprover> approvers, boolean fallbackUsed, String fallbackReason) {
    }

    private record RouteApprover(
        UUID employeeId,
        UUID userId,
        int level,
        String source,
        String roleCode,
        ApprovalSourceType sourceType,
        boolean informational,
        UUID directResponsibleEmployeeId
    ) {
    }
}
