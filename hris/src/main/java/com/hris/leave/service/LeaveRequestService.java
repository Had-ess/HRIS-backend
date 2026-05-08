package com.hris.leave.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.enums.AnalyticsEventType;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InsufficientLeaveBalanceException;
import com.hris.common.exception.InvalidLeavePeriodException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.leave.dto.CreateLeaveRequestDto;
import com.hris.leave.entity.FileAttachment;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.repository.FileAttachmentRepository;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.service.WorkScheduleService;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final WorkScheduleService workScheduleService;
    private final LeaveAttachmentService leaveAttachmentService;
    private final LeaveApprovalWorkflowService leaveApprovalWorkflowService;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final LeaveTypeRepository leaveTypeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AccessScopeService accessScopeService;

    @Transactional
    public LeaveRequest create(CreateLeaveRequestDto dto, UUID requesterId) {
        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        int balanceYear = validateAndResolveBalanceYear(dto.startDate(), dto.endDate());

        if (dto.endDate().isBefore(dto.startDate())) {
            throw new InvalidLeavePeriodException("Leave request end date must be on or after start date");
        }

        LeaveType leaveType = leaveTypeRepository.findById(dto.leaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));

        if (leaveType.isRequiresJustification()
            && (dto.comment() == null || dto.comment().isBlank())) {
            throw new IllegalArgumentException(
                "Leave type '" + leaveType.getName() + "' requires a justification comment");
        }

        int workingDays = workScheduleService.computeWorkingDays(
            dto.startDate(), dto.endDate(), employee.getWorkScheduleId());

        if (workingDays <= 0) {
            throw new IllegalArgumentException("No working days in the selected period");
        }

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeIdAndYear(
                employee.getId(), dto.leaveTypeId(), balanceYear)
            .orElseThrow(() -> new InsufficientLeaveBalanceException(
                "No balance found for this leave type"));

        if (balance.getAvailableDays() < workingDays) {
            throw new InsufficientLeaveBalanceException(
                String.format("Insufficient balance. Available: %d, Requested: %d",
                    balance.getAvailableDays(), workingDays));
        }

        LeaveRequest request = LeaveRequest.builder()
            .employeeId(employee.getId())
            .leaveTypeId(dto.leaveTypeId())
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .workingDays(workingDays)
            .urgencyLevel(dto.urgencyLevel())
            .status(LeaveStatus.PENDING)
            .comment(dto.comment())
            .submittedAt(Instant.now())
            .build();

        LeaveRequest saved = leaveRequestRepository.save(request);

        balance.deductDays(workingDays);
        leaveBalanceRepository.save(balance);

        LeaveApprovalWorkflowService.InstantiatedWorkflow instantiatedWorkflow =
            leaveApprovalWorkflowService.instantiate(saved, employee, leaveType);
        ApprovalWorkflow workflow = instantiatedWorkflow.workflow();
        List<ApprovalStep> steps = instantiatedWorkflow.steps();

        saved.setStatus(LeaveStatus.IN_APPROVAL);
        leaveRequestRepository.save(saved);

        for (ApprovalStep step : steps) {
            if (step.getStatus() == StepStatus.PENDING) {
                notificationPublisher.publishAfterCommit(buildSubmittedEvent(saved, step, employee));
            }
        }
        analyticsEventPublisher.publishLeaveEvent(
            AnalyticsEventType.LEAVE_SUBMITTED,
            saved,
            employee,
            resolvePrimaryAssignment(employee.getId(), saved.getStartDate(), saved.getEndDate()),
            null
        );

        auditLogService.log(requesterId, AuditAction.CREATE, "leave_request",
            saved.getId(), null, saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequest> getMyRequests(UUID requesterId, LeaveStatus status, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (status != null) {
            return leaveRequestRepository.findByEmployeeIdAndStatusOrderBySubmittedAtDesc(
                employee.getId(), status, pageable);
        }
        return leaveRequestRepository.findByEmployeeIdOrderBySubmittedAtDesc(employee.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequest> getVisibleRequests(
            UUID requesterId,
            LeaveStatus status,
            UUID employeeId,
            Pageable pageable) {
        LeaveVisibilityScope scope = resolveLeaveVisibilityScope(requesterId);
        return switch (scope.type()) {
            case GLOBAL -> resolveGlobalVisibleRequests(status, employeeId, pageable);
            case DEPARTMENT -> resolveDepartmentVisibleRequests(scope.departmentId(), status, employeeId, pageable);
        };
    }

    @Transactional(readOnly = true)
    public LeaveRequest getById(UUID id, UUID requesterId) {
        LeaveRequest request = leaveRequestRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canAccessLeaveRequest(request, requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to access this leave request");
        }

        return request;
    }

    @Transactional(readOnly = true)
    public boolean canUploadAttachment(LeaveRequest request, UUID requesterId) {
        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (!request.getEmployeeId().equals(employee.getId())) {
            return false;
        }

        return request.getStatus() == LeaveStatus.PENDING || request.getStatus() == LeaveStatus.IN_APPROVAL;
    }

    @Transactional
    public void cancel(UUID requestId, UUID requesterId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (!request.getEmployeeId().equals(employee.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your leave request");
        }

        ApprovalWorkflow workflow = approvalWorkflowRepository
            .findBySubjectTypeAndSubjectIdForUpdate("LEAVE", requestId)
            .orElse(null);

        request = leaveRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!request.getEmployeeId().equals(employee.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your leave request");
        }

        if (request.getStatus() != LeaveStatus.PENDING && request.getStatus() != LeaveStatus.IN_APPROVAL) {
            if (request.getStatus() == LeaveStatus.APPROVED) {
                throw new IllegalStateException("Cannot cancel an approved leave request");
            }
            throw new IllegalStateException("Cannot cancel a leave request in status: " + request.getStatus());
        }

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeIdAndYear(
                request.getEmployeeId(), request.getLeaveTypeId(),
                validateAndResolveBalanceYear(request.getStartDate(), request.getEndDate()))
            .orElseThrow(() -> new EntityNotFoundException("Balance not found"));

        balance.restoreDays(request.getWorkingDays());
        leaveBalanceRepository.save(balance);

        LeaveStatus previousStatus = request.getStatus();
        request.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(request);

        if (workflow != null) {
            closePendingSteps(workflow.getId(), "Auto-closed due to cancellation");
            workflow.setStatus(WorkflowStatus.CANCELLED);
            workflow.setCompletedAt(Instant.now());
            approvalWorkflowRepository.save(workflow);
        }
        analyticsEventPublisher.publishLeaveEvent(
            AnalyticsEventType.LEAVE_CANCELLED,
            request,
            employee,
            resolvePrimaryAssignment(employee.getId(), request.getStartDate(), request.getEndDate()),
            Instant.now()
        );

        auditLogService.log(requesterId, AuditAction.UPDATE, "leave_request",
            requestId, previousStatus, LeaveStatus.CANCELLED);
    }

    @Transactional
    public void handleWorkflowCompletion(UUID leaveRequestId, WorkflowStatus workflowStatus) {
        handleWorkflowCompletion(leaveRequestId, workflowStatus, null);
    }

    @Transactional
    public void handleWorkflowCompletion(UUID leaveRequestId, WorkflowStatus workflowStatus, UUID actorId) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(leaveRequestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (request.getStatus() == LeaveStatus.CANCELLED) {
            log.info("Ignoring workflow completion for cancelled leave request {}", leaveRequestId);
            return;
        }

        if (request.getStatus() == LeaveStatus.APPROVED || request.getStatus() == LeaveStatus.REJECTED) {
            log.info("Ignoring workflow completion for terminal leave request {}", leaveRequestId);
            return;
        }

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeIdAndYear(
                request.getEmployeeId(), request.getLeaveTypeId(),
                validateAndResolveBalanceYear(request.getStartDate(), request.getEndDate()))
            .orElseThrow(() -> new EntityNotFoundException("Balance not found"));

        LeaveRequest previous = LeaveRequest.builder()
            .id(request.getId())
            .employeeId(request.getEmployeeId())
            .leaveTypeId(request.getLeaveTypeId())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .workingDays(request.getWorkingDays())
            .urgencyLevel(request.getUrgencyLevel())
            .status(request.getStatus())
            .comment(request.getComment())
            .submittedAt(request.getSubmittedAt())
            .build();

        if (workflowStatus == WorkflowStatus.APPROVED) {
            request.setStatus(LeaveStatus.APPROVED);
            balance.confirmUsage(request.getWorkingDays());

            Employee employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
            notificationPublisher.publishAfterCommit(buildApprovedEvent(request, employee));
            analyticsEventPublisher.publishLeaveEvent(
                AnalyticsEventType.LEAVE_APPROVED,
                request,
                employee,
                resolvePrimaryAssignment(employee.getId(), request.getStartDate(), request.getEndDate()),
                Instant.now()
            );
        } else {
            request.setStatus(LeaveStatus.REJECTED);
            balance.restoreDays(request.getWorkingDays());

            Employee employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
            notificationPublisher.publishAfterCommit(buildRejectedEvent(request, employee));
            analyticsEventPublisher.publishLeaveEvent(
                AnalyticsEventType.LEAVE_REJECTED,
                request,
                employee,
                resolvePrimaryAssignment(employee.getId(), request.getStartDate(), request.getEndDate()),
                Instant.now()
            );
        }

        leaveRequestRepository.save(request);
        leaveBalanceRepository.save(balance);

        auditLogService.log(actorId, AuditAction.UPDATE, "leave_request",
            request.getId(), previous, request);
    }

    @Transactional
    public FileAttachment uploadAttachment(UUID requestId, MultipartFile file, UUID uploaderId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canUploadAttachment(request, uploaderId)) {
            Employee employee = employeeRepository.findByUserId(uploaderId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

            if (!request.getEmployeeId().equals(employee.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to upload attachments for this leave request");
            }

            throw new InvalidWorkflowStateException(
                "Attachments can only be uploaded for leave requests that are pending approval");
        }

        return leaveAttachmentService.upload(requestId, file, uploaderId);
    }

    @Transactional(readOnly = true)
    public List<FileAttachment> getAttachments(UUID requestId, UUID accessorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canAccessLeaveAttachments(request, accessorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to access attachments for this leave request");
        }

        return leaveAttachmentService.list(requestId);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(UUID requestId, UUID attachmentId, UUID accessorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canAccessLeaveAttachments(request, accessorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to access attachments for this leave request");
        }

        return leaveAttachmentService.download(requestId, attachmentId);
    }

    @Transactional
    public void deleteAttachment(UUID requestId, UUID attachmentId, UUID requesterId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canUploadAttachment(request, requesterId)) {
            Employee employee = employeeRepository.findByUserId(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

            if (!request.getEmployeeId().equals(employee.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to remove attachments for this leave request");
            }

            throw new InvalidWorkflowStateException(
                "Attachments can only be removed for leave requests that are pending approval");
        }

        leaveAttachmentService.delete(requestId, attachmentId, requesterId);
    }

    private NotificationEvent buildSubmittedEvent(LeaveRequest request,ApprovalStep step,Employee employee) {
        User requester = userRepository.findById(employee.getUserId()).orElseThrow();
        User approver = userRepository.findById(step.getApproverId()).orElseThrow();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("employeeName", requester.getFirstName() + " " + requester.getLastName());
        params.put("startDate", request.getStartDate().toString());
        params.put("endDate", request.getEndDate().toString());
        params.put("workingDays", request.getWorkingDays());
        params.put("linkPath", "/approvals");
        String paramsJson = serializeParams(params);

        return NotificationEvent.builder()
            .eventType(NotificationEventType.LEAVE_SUBMITTED)
            .targetUserId(approver.getId())
            .titleKey("leave.submitted.title")
            .bodyKey("leave.submitted.body")
            .params(paramsJson)
            .locale(approver.getLocalePreference())
            .routingKey("leave.submitted")
            .publishedAt(Instant.now())
            .build();
    }

    private NotificationEvent buildApprovedEvent(LeaveRequest request, Employee employee) {
        User user = userRepository.findById(employee.getUserId()).orElseThrow();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("employeeName", user.getFirstName() + " " + user.getLastName());
        params.put("startDate", request.getStartDate().toString());
        params.put("endDate", request.getEndDate().toString());
        params.put("workingDays", request.getWorkingDays());
        params.put("linkPath", "/leave/" + request.getId());
        String paramsJson = serializeParams(params);

        return NotificationEvent.builder()
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(user.getId())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params(paramsJson)
            .locale(user.getLocalePreference())
            .routingKey("leave.approved")
            .publishedAt(Instant.now())
            .build();
    }

    private NotificationEvent buildRejectedEvent(LeaveRequest request, Employee employee) {
        User user = userRepository.findById(employee.getUserId()).orElseThrow();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("employeeName", user.getFirstName() + " " + user.getLastName());
        params.put("startDate", request.getStartDate().toString());
        params.put("endDate", request.getEndDate().toString());
        params.put("workingDays", request.getWorkingDays());
        params.put("linkPath", "/leave/" + request.getId());
        String paramsJson = serializeParams(params);

        return NotificationEvent.builder()
            .eventType(NotificationEventType.LEAVE_REJECTED)
            .targetUserId(user.getId())
            .titleKey("leave.rejected.title")
            .bodyKey("leave.rejected.body")
            .params(paramsJson)
            .locale(user.getLocalePreference())
            .routingKey("leave.rejected")
            .publishedAt(Instant.now())
            .build();
    }

    private String serializeParams(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification params", e);
        }
    }

    private void closePendingSteps(UUID workflowId, String comment) {
        List<ApprovalStep> pendingSteps = approvalStepRepository.findByWorkflowIdAndStatus(
            workflowId, StepStatus.PENDING);
        if (pendingSteps.isEmpty()) {
            return;
        }

        pendingSteps.forEach(step -> step.skip(comment));
        approvalStepRepository.saveAll(pendingSteps);
    }

    private boolean canAccessLeaveRequest(LeaveRequest request, UUID requesterId) {
        if (hasLeaveOversightAccess(request, requesterId)) {
            return true;
        }

        return employeeRepository.findByUserId(requesterId)
            .map(employee -> request.getEmployeeId().equals(employee.getId()))
            .orElse(false)
            || isPendingApproverForLeaveRequest(request.getId(), requesterId);
    }

    private boolean canAccessLeaveAttachments(LeaveRequest request, UUID requesterId) {
        return canAccessLeaveRequest(request, requesterId);
    }

    private boolean isPendingApproverForLeaveRequest(UUID requestId, UUID requesterId) {
        return approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", requestId)
            .map(workflow -> approvalStepRepository.findByWorkflowIdAndStatus(workflow.getId(), StepStatus.PENDING).stream()
                .anyMatch(step -> requesterId.equals(step.getApproverId())))
            .orElse(false);
    }

    private boolean hasLeaveOversightAccess(LeaveRequest request, UUID userId) {
        if (accessScopeService.hasGlobalBusinessRead(userId)) {
            return true;
        }

        Employee requesterEmployee = accessScopeService.findEmployee(userId).orElse(null);
        return accessScopeService.resolveDepartmentManagerDepartmentId(userId, requesterEmployee)
            .map(departmentId -> requestBelongsToDepartment(request, departmentId))
            .orElse(false);
    }

    private boolean requestBelongsToDepartment(LeaveRequest request, UUID departmentId) {
        return employeeRepository.findById(request.getEmployeeId())
            .map(employee -> departmentId.equals(employee.getDepartmentId()))
            .orElse(false);
    }

    private LeaveVisibilityScope resolveLeaveVisibilityScope(UUID requesterId) {
        if (accessScopeService.hasGlobalBusinessRead(requesterId)) {
            return LeaveVisibilityScope.global();
        }

        Employee requesterEmployee = accessScopeService.findEmployee(requesterId).orElse(null);
        UUID departmentId = accessScopeService.resolveDepartmentManagerDepartmentId(requesterId, requesterEmployee)
            .orElse(null);
        if (departmentId != null) {
            return LeaveVisibilityScope.department(departmentId);
        }

        throw new AccessDeniedException("You are not allowed to browse leave requests");
    }

    private Page<LeaveRequest> resolveGlobalVisibleRequests(
            LeaveStatus status,
            UUID employeeId,
            Pageable pageable) {
        if (employeeId != null) {
            return status == null
                ? leaveRequestRepository.findByEmployeeIdOrderBySubmittedAtDesc(employeeId, pageable)
                : leaveRequestRepository.findByEmployeeIdAndStatusOrderBySubmittedAtDesc(employeeId, status, pageable);
        }
        return status == null
            ? leaveRequestRepository.findAllByOrderBySubmittedAtDesc(pageable)
            : leaveRequestRepository.findByStatusOrderBySubmittedAtDesc(status, pageable);
    }

    private Page<LeaveRequest> resolveDepartmentVisibleRequests(
            UUID departmentId,
            LeaveStatus status,
            UUID employeeId,
            Pageable pageable) {
        if (employeeId != null) {
            Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            if (!departmentId.equals(employee.getDepartmentId())) {
                throw new AccessDeniedException("You are not allowed to browse leave requests for this employee");
            }
            return status == null
                ? leaveRequestRepository.findByDepartmentIdAndEmployeeIdOrderBySubmittedAtDesc(
                    departmentId, employeeId, pageable)
                : leaveRequestRepository.findByDepartmentIdAndEmployeeIdAndStatusOrderBySubmittedAtDesc(
                    departmentId, employeeId, status, pageable);
        }
        return status == null
            ? leaveRequestRepository.findByDepartmentIdOrderBySubmittedAtDesc(departmentId, pageable)
            : leaveRequestRepository.findByDepartmentIdAndStatusOrderBySubmittedAtDesc(departmentId, status, pageable);
    }

    private int validateAndResolveBalanceYear(LocalDate startDate, LocalDate endDate) {
        if (startDate.getYear() != endDate.getYear()) {
            throw new InvalidLeavePeriodException("Leave request cannot span multiple calendar years");
        }
        return startDate.getYear();
    }

    private ProjectAssignment resolvePrimaryAssignment(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        List<ProjectAssignment> assignments = projectAssignmentRepository
            .findActiveAssignmentsDuringPeriod(employeeId, startDate, endDate);
        if (assignments == null || assignments.isEmpty()) {
            return null;
        }
        return assignments.getFirst();
    }

    private record LeaveVisibilityScope(LeaveVisibilityScopeType type, UUID departmentId) {
        static LeaveVisibilityScope global() {
            return new LeaveVisibilityScope(LeaveVisibilityScopeType.GLOBAL, null);
        }

        static LeaveVisibilityScope department(UUID departmentId) {
            return new LeaveVisibilityScope(LeaveVisibilityScopeType.DEPARTMENT, departmentId);
        }
    }

    private enum LeaveVisibilityScopeType {
        GLOBAL,
        DEPARTMENT
    }

}
