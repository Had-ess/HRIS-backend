package com.hris.approval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.service.UserDisplayNameService;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.organisation.entity.Project;
import com.hris.organisation.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalStepQueryService {

    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final ObjectMapper objectMapper;
    private final UserDisplayNameService userDisplayNameService;

    @Transactional(readOnly = true)
    public Page<ApprovalStepResponseDto> toPendingPage(Page<ApprovalStep> steps) {
        return steps.map(this::toStepDto);
    }

    private ApprovalStepResponseDto toStepDto(ApprovalStep step) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findById(step.getWorkflowId()).orElse(null);
        SubjectDetails subjectDetails = resolveSubjectDetails(workflow, step);

        return new ApprovalStepResponseDto(
            step.getId(),
            step.getWorkflowId(),
            workflow != null ? workflow.getSubjectType() : null,
            workflow != null ? workflow.getSubjectId() : null,
            subjectDetails.reference(),
            subjectDetails.requesterName(),
            subjectDetails.departmentName(),
            subjectDetails.projectName(),
            step.getApproverId(),
            resolveApproverName(step.getApproverId()),
            step.getStepOrder(),
            step.getStatus(),
            step.getContext(),
            step.getRoutingSnapshot(),
            step.getComment(),
            step.getDecidedAt()
        );
    }

    private String resolveApproverName(UUID approverId) {
        return userDisplayNameService.resolveDisplayName(approverId);
    }

    private SubjectDetails resolveSubjectDetails(ApprovalWorkflow workflow, ApprovalStep step) {
        if (workflow == null || workflow.getSubjectType() == null || workflow.getSubjectId() == null) {
            return SubjectDetails.empty();
        }

        return switch (workflow.getSubjectType()) {
            case "LEAVE" -> resolveLeaveSubjectDetails(workflow.getSubjectId(), step.getRoutingSnapshot());
            case "ADMIN_REQUEST" -> resolveAdminRequestSubjectDetails(workflow.getSubjectId());
            default -> SubjectDetails.withReference(workflow.getSubjectId().toString());
        };
    }

    private SubjectDetails resolveLeaveSubjectDetails(UUID subjectId, String routingSnapshot) {
        LeaveRequest request = leaveRequestRepository.findById(subjectId).orElse(null);
        if (request == null) {
            return SubjectDetails.withReference(subjectId.toString());
        }

        Employee employee = employeeRepository.findById(request.getEmployeeId()).orElse(null);
        User requester = employee != null ? userRepository.findById(employee.getUserId()).orElse(null) : null;

        String requesterName = userDisplayNameService.toDisplayName(requester);
        String departmentName = employee != null ? resolveDepartmentName(employee.getDepartmentId()) : null;
        String projectName = resolveProjectNameFromSnapshot(routingSnapshot);

        return new SubjectDetails(
            request.getId().toString(),
            requesterName,
            departmentName,
            projectName
        );
    }

    private SubjectDetails resolveAdminRequestSubjectDetails(UUID subjectId) {
        AdminRequest request = adminRequestRepository.findById(subjectId).orElse(null);
        if (request == null) {
            return SubjectDetails.withReference(subjectId.toString());
        }

        User requester = userRepository.findById(request.getRequesterUserId()).orElse(null);

        return new SubjectDetails(
            request.getRequestNumber() != null && !request.getRequestNumber().isBlank()
                ? request.getRequestNumber()
                : request.getId().toString(),
            userDisplayNameService.toDisplayName(requester),
            null,
            null
        );
    }

    private String resolveDepartmentName(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }

        return departmentRepository.findById(departmentId)
            .map(Department::getName)
            .filter(name -> name != null && !name.isBlank())
            .orElse(null);
    }

    private String resolveProjectNameFromSnapshot(String routingSnapshot) {
        String projectId = parseRoutingSnapshot(routingSnapshot).get("projectId");
        if (projectId == null || projectId.isBlank()) {
            return null;
        }

        try {
            return projectRepository.findById(UUID.fromString(projectId))
                .map(Project::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, String> parseRoutingSnapshot(String routingSnapshot) {
        if (routingSnapshot == null || routingSnapshot.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(routingSnapshot, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private record SubjectDetails(
        String reference,
        String requesterName,
        String departmentName,
        String projectName
    ) {
        private static SubjectDetails empty() {
            return new SubjectDetails(null, null, null, null);
        }

        private static SubjectDetails withReference(String reference) {
            return new SubjectDetails(reference, null, null, null);
        }
    }
}
