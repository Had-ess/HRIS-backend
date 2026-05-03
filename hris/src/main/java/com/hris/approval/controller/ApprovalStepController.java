package com.hris.approval.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.dto.ApprovalCommentDto;
import com.hris.approval.dto.ApprovalDecisionDto;
import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.ApprovalStepService;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.organisation.entity.Project;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/approval-steps")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApprovalStepController {

    private final ApprovalStepService approvalStepService;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalStepResponseDto>>> getMyPending(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<ApprovalStep> steps = approvalStepService.getPendingForApprover(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(steps.map(this::toStepDto))));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApprovalCommentDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        approvalStepService.approve(id, dto != null ? dto.comment() : null, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        approvalStepService.reject(id, dto.comment(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
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
        return userRepository.findById(approverId)
            .map(this::toDisplayName)
            .orElse(null);
    }

    private String toDisplayName(User user) {
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
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

        String requesterName = requester != null ? toDisplayName(requester) : null;
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

        User requester = userRepository.findById(request.getRequesterId()).orElse(null);

        return new SubjectDetails(
            request.getTrackingNumber() != null && !request.getTrackingNumber().isBlank()
                ? request.getTrackingNumber()
                : request.getId().toString(),
            requester != null ? toDisplayName(requester) : null,
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
            return objectMapper.readValue(routingSnapshot, new TypeReference<>() {});
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
