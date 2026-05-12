package com.hris.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.admin.dto.AdminRequestCommentCreateDto;
import com.hris.admin.dto.AdminRequestInboxSummaryDto;
import com.hris.admin.dto.AdminRequestRejectDto;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.dto.UpdateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestAttachment;
import com.hris.admin.entity.AdminRequestComment;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestAttachmentRepository;
import com.hris.admin.repository.AdminRequestCommentRepository;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidAdminRequestStateException;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private final AdminRequestRepository adminRequestRepository;
    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final AdminRequestAttachmentRepository attachmentRepository;
    private final AdminRequestCommentRepository commentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AccessScopeService accessScopeService;
    private final AdminRequestAttachmentService attachmentService;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminRequest create(CreateAdminRequestDto dto, UUID requesterUserId) {
        Employee requesterEmployee = employeeRepository.findByUserId(requesterUserId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        AdminRequestType requestType = resolveActiveType(dto.requestTypeId());

        Instant now = Instant.now();
        AdminRequest saved = adminRequestRepository.save(AdminRequest.builder()
            .requestNumber(AdminRequest.generateRequestNumber())
            .requesterEmployeeId(requesterEmployee.getId())
            .requesterUserId(requesterUserId)
            .typeId(requestType.getId())
            .subject(normalizeRequired(dto.subject(), "subject"))
            .description(normalizeRequired(dto.description(), "description"))
            .status(AdminRequestStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build());

        auditLogService.log(requesterUserId, AuditAction.CREATE, "admin_request", saved.getId(), null, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_CREATED, requesterUserId,
            "admin.created.title", "admin.created.body", "admin.created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AdminRequest> getMyRequests(UUID requesterUserId, Pageable pageable) {
        return adminRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId, pageable);
    }

    @Transactional(readOnly = true)
    public AdminRequest getOwnRequest(UUID requestId, UUID requesterUserId) {
        AdminRequest request = findRequest(requestId);
        ensureOwner(request, requesterUserId);
        return request;
    }

    @Transactional
    public AdminRequest update(UUID requestId, UpdateAdminRequestDto dto, UUID requesterUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        ensureOwner(request, requesterUserId);
        if (request.getStatus() != AdminRequestStatus.DRAFT) {
            throw new InvalidAdminRequestStateException("Only draft admin requests can be edited");
        }

        AdminRequest previous = snapshot(request);
        if (dto.requestTypeId() != null && !dto.requestTypeId().equals(request.getTypeId())) {
            request.setTypeId(resolveActiveType(dto.requestTypeId()).getId());
        }
        if (dto.subject() != null) {
            request.setSubject(normalizeRequired(dto.subject(), "subject"));
        }
        if (dto.description() != null) {
            request.setDescription(normalizeRequired(dto.description(), "description"));
        }
        request.setUpdatedAt(Instant.now());
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(requesterUserId, AuditAction.UPDATE, "admin_request", requestId, previous, saved);
        return saved;
    }

    @Transactional
    public AdminRequest submit(UUID requestId, UUID requesterUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        ensureOwner(request, requesterUserId);
        if (request.getStatus() != AdminRequestStatus.DRAFT) {
            throw new InvalidAdminRequestStateException("Only draft admin requests can be submitted");
        }

        AdminRequestType requestType = resolveActiveType(request.getTypeId());
        if (requestType.isRequiresAttachment()
            && attachmentRepository.findByAdminRequestIdOrderByUploadedAtAsc(requestId).isEmpty()) {
            throw new IllegalStateException("This request type requires at least one attachment before submission");
        }

        AdminRequest previous = snapshot(request);
        Instant now = Instant.now();
        request.setStatus(AdminRequestStatus.SUBMITTED);
        request.setSubmittedAt(now);
        request.setDueAt(calculateDueAt(now, requestType.getSlaHours()));
        request.setUpdatedAt(now);
        AdminRequest saved = adminRequestRepository.save(request);

        auditLogService.log(requesterUserId, AuditAction.UPDATE, "admin_request", requestId, previous, saved);
        notifyProcessors(saved, requestType, NotificationEventType.ADMIN_REQUEST_SUBMITTED,
            "admin.submitted.title", "admin.submitted.body", "admin.submitted");
        return saved;
    }

    @Transactional
    public AdminRequest cancel(UUID requestId, UUID requesterUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        ensureOwner(request, requesterUserId);
        if (!canCancel(request)) {
            throw new InvalidAdminRequestStateException(
                "Admin request cannot be cancelled from status: " + request.getStatus());
        }

        AdminRequest previous = snapshot(request);
        request.setStatus(AdminRequestStatus.CANCELLED);
        request.setUpdatedAt(Instant.now());
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(requesterUserId, AuditAction.UPDATE, "admin_request", requestId, previous, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_CANCELLED, requesterUserId,
            "admin.cancelled.title", "admin.cancelled.body", "admin.cancelled");
        return saved;
    }

    @Transactional
    public AdminRequestAttachment uploadRequesterAttachment(UUID requestId, MultipartFile file, UUID requesterUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        ensureOwner(request, requesterUserId);
        if (request.getStatus() != AdminRequestStatus.DRAFT && !canCancel(request) && request.getStatus() != AdminRequestStatus.SUBMITTED) {
            throw new InvalidAdminRequestStateException("Requester attachments are only allowed for draft or unprocessed submitted requests");
        }

        AdminRequestAttachment attachment = attachmentService.store(requestId, file, requesterUserId, false);
        auditLogService.log(requesterUserId, AuditAction.CREATE, "admin_request_attachment", attachment.getId(), null, attachment);
        publishCommentOrAttachmentEvent(request, NotificationEventType.ADMIN_REQUEST_ATTACHMENT_ADDED,
            "admin.attachment_added.title", "admin.attachment_added.body", "admin.attachment_added", false);
        return attachment;
    }

    @Transactional(readOnly = true)
    public Page<AdminRequest> searchInbox(
            String search,
            UUID typeId,
            AdminRequestStatus status,
            String requester,
            Instant dateFrom,
            Instant dateTo,
            Boolean overdue,
            Pageable pageable) {
        Specification<AdminRequest> specification = Specification.where((root, query, cb) ->
            cb.notEqual(root.get("status"), AdminRequestStatus.DRAFT));

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("requestNumber")), pattern),
                cb.like(cb.lower(root.get("subject")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        if (typeId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("typeId"), typeId));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (requester != null && !requester.isBlank()) {
            List<UUID> requesterIds = userRepository.searchIds(requester.trim());
            if (requesterIds.isEmpty()) {
                specification = specification.and((root, query, cb) -> cb.disjunction());
            } else {
                specification = specification.and((root, query, cb) -> root.get("requesterUserId").in(requesterIds));
            }
        }
        if (dateFrom != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
        }
        if (dateTo != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
        }
        if (Boolean.TRUE.equals(overdue)) {
            specification = specification.and((root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueAt")),
                cb.lessThan(root.get("dueAt"), Instant.now()),
                root.get("status").in(List.of(AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_REVIEW, AdminRequestStatus.APPROVED))
            ));
        }

        return adminRequestRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public AdminRequestInboxSummaryDto getInboxSummary() {
        Instant now = Instant.now();
        long overdue = adminRequestRepository.count((root, query, cb) -> cb.and(
            cb.isNotNull(root.get("dueAt")),
            cb.lessThan(root.get("dueAt"), now),
            root.get("status").in(List.of(AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_REVIEW, AdminRequestStatus.APPROVED))
        ));
        return new AdminRequestInboxSummaryDto(
            adminRequestRepository.count((root, query, cb) -> cb.equal(root.get("status"), AdminRequestStatus.SUBMITTED)),
            adminRequestRepository.count((root, query, cb) -> cb.equal(root.get("status"), AdminRequestStatus.IN_REVIEW)),
            overdue,
            adminRequestRepository.count((root, query, cb) -> cb.equal(root.get("status"), AdminRequestStatus.COMPLETED))
        );
    }

    @Transactional(readOnly = true)
    public AdminRequest getInboxRequest(UUID requestId) {
        AdminRequest request = findRequest(requestId);
        if (request.getStatus() == AdminRequestStatus.DRAFT) {
            throw new EntityNotFoundException("Admin request not found");
        }
        return request;
    }

    @Transactional
    public AdminRequest startReview(UUID requestId, UUID processorUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        transition(request, AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_REVIEW,
            "Admin request can only move to IN_REVIEW from SUBMITTED");

        AdminRequest previous = snapshot(request);
        Instant now = Instant.now();
        request.setReviewedAt(now);
        request.setProcessedByUserId(processorUserId);
        request.setUpdatedAt(now);
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(processorUserId, AuditAction.UPDATE, "admin_request", requestId, previous, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_IN_REVIEW, saved.getRequesterUserId(),
            "admin.in_review.title", "admin.in_review.body", "admin.in_review");
        return saved;
    }

    @Transactional
    public AdminRequest approve(UUID requestId, UUID processorUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        if (request.getStatus() != AdminRequestStatus.SUBMITTED && request.getStatus() != AdminRequestStatus.IN_REVIEW) {
            throw new InvalidAdminRequestStateException(
                "Admin request cannot be approved from status: " + request.getStatus());
        }

        AdminRequest previous = snapshot(request);
        Instant now = Instant.now();
        request.setStatus(AdminRequestStatus.APPROVED);
        request.setDecidedAt(now);
        request.setProcessedByUserId(processorUserId);
        request.setUpdatedAt(now);
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(processorUserId, AuditAction.APPROVE, "admin_request", requestId, previous, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_APPROVED, saved.getRequesterUserId(),
            "admin.approved.title", "admin.approved.body", "admin.approved");
        return saved;
    }

    @Transactional
    public AdminRequest reject(UUID requestId, UUID processorUserId, AdminRequestRejectDto dto) {
        AdminRequest request = findRequestForUpdate(requestId);
        if (request.getStatus() != AdminRequestStatus.SUBMITTED && request.getStatus() != AdminRequestStatus.IN_REVIEW) {
            throw new InvalidAdminRequestStateException(
                "Admin request cannot be rejected from status: " + request.getStatus());
        }

        String reason = normalizeRequired(dto.reason(), "reason");
        AdminRequest previous = snapshot(request);
        Instant now = Instant.now();
        request.setStatus(AdminRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setDecidedAt(now);
        request.setProcessedByUserId(processorUserId);
        request.setUpdatedAt(now);
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(processorUserId, AuditAction.REJECT, "admin_request", requestId, previous, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_REJECTED, saved.getRequesterUserId(),
            "admin.rejected.title", "admin.rejected.body", "admin.rejected");
        return saved;
    }

    @Transactional
    public AdminRequest complete(UUID requestId, UUID processorUserId) {
        AdminRequest request = findRequestForUpdate(requestId);
        transition(request, AdminRequestStatus.APPROVED, AdminRequestStatus.COMPLETED,
            "Admin request can only be completed from APPROVED");

        AdminRequest previous = snapshot(request);
        Instant now = Instant.now();
        request.setCompletedAt(now);
        request.setProcessedByUserId(processorUserId);
        request.setUpdatedAt(now);
        AdminRequest saved = adminRequestRepository.save(request);
        auditLogService.log(processorUserId, AuditAction.UPDATE, "admin_request", requestId, previous, saved);
        publishToUser(saved, NotificationEventType.ADMIN_REQUEST_COMPLETED, saved.getRequesterUserId(),
            "admin.completed.title", "admin.completed.body", "admin.completed");
        return saved;
    }

    @Transactional
    public AdminRequestComment addComment(UUID requestId, UUID authorUserId, AdminRequestCommentCreateDto dto) {
        AdminRequest request = findRequest(requestId);
        boolean processor = isProcessor(authorUserId);
        if (!processor) {
            ensureOwner(request, authorUserId);
        }

        AdminRequestComment saved = commentRepository.save(AdminRequestComment.builder()
            .adminRequestId(requestId)
            .authorUserId(authorUserId)
            .comment(normalizeRequired(dto.comment(), "comment"))
            .internal(processor && Boolean.TRUE.equals(dto.internal()))
            .createdAt(Instant.now())
            .build());

        auditLogService.log(authorUserId, AuditAction.CREATE, "admin_request_comment", saved.getId(), null, saved);
        if (!saved.isInternal()) {
            publishCommentOrAttachmentEvent(request, NotificationEventType.ADMIN_REQUEST_COMMENT_ADDED,
                "admin.comment_added.title", "admin.comment_added.body", "admin.comment_added", processor);
        }
        return saved;
    }

    @Transactional
    public AdminRequestAttachment uploadResponseAttachment(UUID requestId, MultipartFile file, UUID processorUserId) {
        AdminRequest request = findRequest(requestId);
        AdminRequestAttachment attachment = attachmentService.store(requestId, file, processorUserId, true);
        auditLogService.log(processorUserId, AuditAction.CREATE, "admin_request_attachment", attachment.getId(), null, attachment);
        publishCommentOrAttachmentEvent(request, NotificationEventType.ADMIN_REQUEST_ATTACHMENT_ADDED,
            "admin.response_document_added.title", "admin.response_document_added.body", "admin.response_document_added", true);
        return attachment;
    }

    @Transactional(readOnly = true)
    public boolean canViewInternal(UUID userId) {
        return isProcessor(userId);
    }

    private AdminRequestType resolveActiveType(UUID typeId) {
        return adminRequestTypeRepository.findById(typeId)
            .filter(AdminRequestType::isActive)
            .orElseThrow(() -> new EntityNotFoundException("Admin request type not found or inactive"));
    }

    private AdminRequest findRequest(UUID requestId) {
        return adminRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));
    }

    private AdminRequest findRequestForUpdate(UUID requestId) {
        return adminRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));
    }

    private void ensureOwner(AdminRequest request, UUID requesterUserId) {
        if (!request.getRequesterUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("You can only access your own admin requests");
        }
    }

    private boolean canCancel(AdminRequest request) {
        if (request.getStatus() == AdminRequestStatus.DRAFT) {
            return true;
        }
        return request.getStatus() == AdminRequestStatus.SUBMITTED
            && request.getProcessedByUserId() == null
            && request.getReviewedAt() == null
            && request.getDecidedAt() == null;
    }

    private void transition(
            AdminRequest request,
            AdminRequestStatus from,
            AdminRequestStatus to,
            String errorMessage) {
        if (request.getStatus() != from) {
            throw new InvalidAdminRequestStateException(errorMessage);
        }
        request.setStatus(to);
    }

    private Instant calculateDueAt(Instant submittedAt, Integer slaHours) {
        if (submittedAt == null || slaHours == null) {
            return null;
        }
        return submittedAt.plus(slaHours, ChronoUnit.HOURS);
    }

    private boolean isProcessor(UUID userId) {
        return accessScopeService.hasAnyPermissionName(userId,
            "ADMIN_REQUEST_INBOX_READ",
            "ADMIN_REQUEST_READ_GLOBAL",
            "ADMIN_REQUEST_PROCESS",
            "ADMIN_REQUEST_APPROVE",
            "ADMIN_REQUEST_REJECT",
            "ADMIN_REQUEST_COMPLETE");
    }

    private void notifyProcessors(
            AdminRequest request,
            AdminRequestType type,
            NotificationEventType eventType,
            String titleKey,
            String bodyKey,
            String routingKey) {
        List<User> processors = userRepository.findByPermissionNames(List.of(
            "ADMIN_REQUEST_INBOX_READ",
            "ADMIN_REQUEST_READ_GLOBAL",
            "ADMIN_REQUEST_PROCESS",
            "ADMIN_REQUEST_APPROVE",
            "ADMIN_REQUEST_REJECT",
            "ADMIN_REQUEST_COMPLETE"
        ));
        for (User processor : processors) {
            notificationPublisher.publishAfterCommit(buildEvent(
                eventType,
                processor.getId(),
                processor.getLocalePreference(),
                titleKey,
                bodyKey,
                routingKey,
                request,
                type.getName(),
                null
            ));
        }
    }

    private void publishToUser(
            AdminRequest request,
            NotificationEventType eventType,
            UUID targetUserId,
            String titleKey,
            String bodyKey,
            String routingKey) {
        User targetUser = userRepository.findById(targetUserId).orElseThrow();
        String typeName = adminRequestTypeRepository.findById(request.getTypeId())
            .map(AdminRequestType::getName)
            .orElse(null);
        notificationPublisher.publishAfterCommit(buildEvent(
            eventType,
            targetUserId,
            targetUser.getLocalePreference(),
            titleKey,
            bodyKey,
            routingKey,
            request,
            typeName,
            request.getRejectionReason()
        ));
    }

    private void publishCommentOrAttachmentEvent(
            AdminRequest request,
            NotificationEventType eventType,
            String titleKey,
            String bodyKey,
            String routingKey,
            boolean fromProcessor) {
        if (fromProcessor) {
            publishToUser(request, eventType, request.getRequesterUserId(), titleKey, bodyKey, routingKey);
            return;
        }
        notifyProcessors(
            request,
            adminRequestTypeRepository.findById(request.getTypeId()).orElseThrow(),
            eventType,
            titleKey,
            bodyKey,
            routingKey
        );
    }

    private NotificationEvent buildEvent(
            NotificationEventType eventType,
            UUID targetUserId,
            String locale,
            String titleKey,
            String bodyKey,
            String routingKey,
            AdminRequest request,
            String typeName,
            String rejectionReason) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("requestNumber", request.getRequestNumber());
        params.put("subject", request.getSubject());
        params.put("requestType", typeName == null ? "" : typeName);
        params.put("rejectionReason", rejectionReason == null ? "" : rejectionReason);
        params.put("linkPath", "/requests");

        return NotificationEvent.builder()
            .eventType(eventType)
            .targetUserId(targetUserId)
            .titleKey(titleKey)
            .bodyKey(bodyKey)
            .params(serializeParams(params))
            .locale(locale == null || locale.isBlank() ? "fr" : locale)
            .routingKey(routingKey)
            .publishedAt(Instant.now())
            .build();
    }

    private String serializeParams(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification payload", ex);
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private AdminRequest snapshot(AdminRequest request) {
        return AdminRequest.builder()
            .id(request.getId())
            .requestNumber(request.getRequestNumber())
            .requesterEmployeeId(request.getRequesterEmployeeId())
            .requesterUserId(request.getRequesterUserId())
            .typeId(request.getTypeId())
            .subject(request.getSubject())
            .description(request.getDescription())
            .status(request.getStatus())
            .submittedAt(request.getSubmittedAt())
            .reviewedAt(request.getReviewedAt())
            .decidedAt(request.getDecidedAt())
            .completedAt(request.getCompletedAt())
            .dueAt(request.getDueAt())
            .processedByUserId(request.getProcessedByUserId())
            .rejectionReason(request.getRejectionReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }
}
