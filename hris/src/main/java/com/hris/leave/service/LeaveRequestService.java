package com.hris.leave.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.ApprovalRouter;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.FileAttachmentValidationException;
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
import com.hris.notification.service.NotificationPublisher;
import com.hris.organisation.service.WorkScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.InputStreamResource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_ATTACHMENT_MIME_TYPES = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
        "pdf",
        "png",
        "jpg",
        "jpeg"
    );
    private static final Map<String, String> ATTACHMENT_MIME_TYPE_BY_EXTENSION = Map.of(
        "pdf", "application/pdf",
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg"
    );
    private static final byte[] PDF_SIGNATURE = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE_PREFIX = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final ApprovalRouter approvalRouter;
    private final WorkScheduleService workScheduleService;
    private final FileStorageService fileStorageService;
    private final NotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final LeaveTypeRepository leaveTypeRepository;

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

        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
            .subjectType("LEAVE")
            .subjectId(saved.getId())
            .status(WorkflowStatus.INITIATED)
            .createdAt(Instant.now())
            .build();

        workflow = approvalWorkflowRepository.save(workflow);

        List<ApprovalStep> steps = approvalRouter.resolveSteps(
            employee.getId(), workflow.getId(), dto.startDate(), dto.endDate());

        if (steps.isEmpty()) {
            throw new InvalidWorkflowStateException(
                "No approvers could be resolved for this leave request");
        }

        saved.setStatus(LeaveStatus.IN_APPROVAL);
        leaveRequestRepository.save(saved);

        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        approvalWorkflowRepository.save(workflow);

        for (ApprovalStep step : steps) {
            notificationPublisher.publish(buildSubmittedEvent(saved, step, employee));
        }

        auditLogService.log(requesterId, AuditAction.CREATE, "leave_request",
            saved.getId(), null, saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequest> getMyRequests(UUID requesterId, LeaveStatus status, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (status != null) {
            return leaveRequestRepository.findByEmployeeIdAndStatus(employee.getId(), status, pageable);
        }
        return leaveRequestRepository.findByEmployeeId(employee.getId(), pageable);
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
            workflow.setStatus(WorkflowStatus.REJECTED);
            workflow.setCompletedAt(Instant.now());
            approvalWorkflowRepository.save(workflow);
        }

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

        if (workflowStatus == WorkflowStatus.COMPLETED) {
            request.setStatus(LeaveStatus.APPROVED);
            balance.confirmUsage(request.getWorkingDays());

            Employee employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
            notificationPublisher.publish(buildApprovedEvent(request, employee));
        } else {
            request.setStatus(LeaveStatus.REJECTED);
            balance.restoreDays(request.getWorkingDays());

            Employee employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
            notificationPublisher.publish(buildRejectedEvent(request, employee));
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

        Employee employee = employeeRepository.findByUserId(uploaderId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (!request.getEmployeeId().equals(employee.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to upload attachments for this leave request");
        }

        if (request.getStatus() != LeaveStatus.PENDING && request.getStatus() != LeaveStatus.IN_APPROVAL) {
            throw new InvalidWorkflowStateException(
                "Attachments can only be uploaded for leave requests that are pending approval");
        }

        String detectedMimeType = validateAttachment(file);
        String sanitizedFileName = sanitizeAttachmentFilename(file.getOriginalFilename());

        String storagePath = fileStorageService.store(file, requestId);

        FileAttachment attachment = FileAttachment.builder()
            .requestId(requestId)
            .fileName(sanitizedFileName)
            .mimeType(detectedMimeType)
            .storagePath(storagePath)
            .uploadedById(uploaderId)
            .uploadedAt(Instant.now())
            .build();

        FileAttachment saved = fileAttachmentRepository.save(attachment);

        auditLogService.log(uploaderId, AuditAction.CREATE, "file_attachment",
            saved.getId(), null, saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<FileAttachment> getAttachments(UUID requestId, UUID accessorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canAccessLeaveAttachments(request, accessorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to access attachments for this leave request");
        }

        return fileAttachmentRepository.findByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(UUID requestId, UUID attachmentId, UUID accessorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));

        if (!canAccessLeaveAttachments(request, accessorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not allowed to access attachments for this leave request");
        }

        FileAttachment attachment = fileAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));

        if (!attachment.getRequestId().equals(requestId)) {
            throw new EntityNotFoundException("Attachment not found");
        }

        return new AttachmentDownload(
            attachment.getFileName(),
            attachment.getMimeType(),
            new InputStreamResource(fileStorageService.retrieve(attachment.getStoragePath()))
        );
    }

    private NotificationEvent buildSubmittedEvent(LeaveRequest request,ApprovalStep step,Employee employee) {
        User requester = userRepository.findById(employee.getUserId()).orElseThrow();
        User approver = userRepository.findById(step.getApproverId()).orElseThrow();

        String paramsJson = serializeParams(List.of(
            requester.getFirstName() + " " + requester.getLastName(),
            request.getStartDate().toString(),
            request.getEndDate().toString(),
            request.getWorkingDays()
        ));

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
        String paramsJson = serializeParams(List.of(
            user.getFirstName() + " " + user.getLastName(),
            request.getStartDate().toString(),
            request.getEndDate().toString(),
            request.getWorkingDays()
        ));

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
        String paramsJson = serializeParams(List.of(
            user.getFirstName() + " " + user.getLastName(),
            request.getStartDate().toString(),
            request.getEndDate().toString(),
            request.getWorkingDays()
        ));

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

    private String serializeParams(List<?> params) {
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

        pendingSteps.forEach(step -> step.reject(comment));
        approvalStepRepository.saveAll(pendingSteps);
    }

    private boolean canAccessLeaveRequest(LeaveRequest request, UUID requesterId) {
        if (hasLeaveOversightAccess(requesterId)) {
            return true;
        }

        Employee employee = employeeRepository.findByUserId(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        return request.getEmployeeId().equals(employee.getId());
    }

    private boolean canAccessLeaveAttachments(LeaveRequest request, UUID requesterId) {
        if (canAccessLeaveRequest(request, requesterId)) {
            return true;
        }

        return approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", request.getId())
            .map(workflow -> approvalStepRepository.findByWorkflowId(workflow.getId()).stream()
                .anyMatch(step -> requesterId.equals(step.getApproverId())
                    && step.getStatus() == StepStatus.PENDING))
            .orElse(false);
    }

    private boolean hasLeaveOversightAccess(UUID userId) {
        return userRoleRepository.findByUserIdAndIsActiveTrue(userId).stream()
            .anyMatch(userRole -> userRole.getRole() != null
                && ("HR_ADMIN".equals(userRole.getRole().getCode())
                    || "ADMINISTRATION".equals(userRole.getRole().getCode())));
    }

    private int validateAndResolveBalanceYear(LocalDate startDate, LocalDate endDate) {
        if (startDate.getYear() != endDate.getYear()) {
            throw new InvalidLeavePeriodException("Leave request cannot span multiple calendar years");
        }
        return startDate.getYear();
    }

    private String validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileAttachmentValidationException("Attachment file is required");
        }

        String sanitizedFilename = sanitizeAttachmentFilename(file.getOriginalFilename());
        int extensionSeparator = sanitizedFilename.lastIndexOf('.');
        String extension = extensionSeparator >= 0
            ? sanitizedFilename.substring(extensionSeparator + 1).toLowerCase()
            : "";

        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new FileAttachmentValidationException(
                "Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
        }

        String detectedMimeType = detectAttachmentMimeType(file);
        if (!ALLOWED_ATTACHMENT_MIME_TYPES.contains(detectedMimeType)) {
            throw new FileAttachmentValidationException(
                "Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
        }

        String expectedMimeType = ATTACHMENT_MIME_TYPE_BY_EXTENSION.get(extension);
        if (!detectedMimeType.equals(expectedMimeType)) {
            throw new FileAttachmentValidationException(
                "Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
        }

        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new FileAttachmentValidationException(
                "Attachment exceeds the maximum allowed size of 10 MB");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType != null
            && !declaredContentType.isBlank()
            && !detectedMimeType.equals(declaredContentType.toLowerCase())) {
            throw new FileAttachmentValidationException(
                "Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
        }

        return detectedMimeType;
    }

    private String detectAttachmentMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(PNG_SIGNATURE.length);

            if (startsWith(header, PDF_SIGNATURE)) {
                return "application/pdf";
            }
            if (startsWith(header, PNG_SIGNATURE)) {
                return "image/png";
            }
            if (startsWith(header, JPEG_SIGNATURE_PREFIX)) {
                return "image/jpeg";
            }
        } catch (IOException e) {
            throw new FileAttachmentValidationException("Failed to read attachment content");
        }

        throw new FileAttachmentValidationException(
            "Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");
    }

    private boolean startsWith(byte[] actual, byte[] expectedPrefix) {
        if (actual.length < expectedPrefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(actual, expectedPrefix.length), expectedPrefix);
    }

    private String sanitizeAttachmentFilename(String originalFilename) {
        String sanitizedFilename = fileStorageService.sanitizeFilename(originalFilename);
        if (sanitizedFilename == null || sanitizedFilename.isBlank()) {
            return "unknown";
        }
        return sanitizedFilename;
    }
}
