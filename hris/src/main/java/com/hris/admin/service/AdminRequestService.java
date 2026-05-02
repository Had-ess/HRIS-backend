package com.hris.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidAdminRequestStateException;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestService {

    private final AdminRequestRepository adminRequestRepository;
    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final UserRepository userRepository;
    private final NotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminRequest create(CreateAdminRequestDto dto, UUID requesterId) {
        AdminRequestType requestType = adminRequestTypeRepository.findById(dto.requestTypeId())
            .filter(AdminRequestType::isActive)
            .orElseThrow(() -> new EntityNotFoundException("Admin request type not found or inactive"));

        String trackingNumber = AdminRequest.generateTrackingNumber();

        AdminRequest request = AdminRequest.builder()
            .requesterId(requesterId)
            .requestTypeId(dto.requestTypeId())
            .trackingNumber(trackingNumber)
            .description(dto.description())
            .urgencyLevel(dto.urgencyLevel())
            .status(AdminRequestStatus.SUBMITTED)
            .metadata(dto.metadata())
            .submittedAt(Instant.now())
            .build();

        AdminRequest saved = adminRequestRepository.save(request);

        auditLogService.log(requesterId, AuditAction.CREATE, "admin_request",
            saved.getId(), null, saved);

        notificationPublisher.publish(buildSubmittedEvent(saved, requesterId, requestType));

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AdminRequest> getMyRequests(UUID requesterId, Pageable pageable) {
        return adminRequestRepository.findByRequesterIdOrderBySubmittedAtDesc(requesterId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminRequest> getIncoming(Pageable pageable) {
        return adminRequestRepository.findByStatusInOrderBySubmittedAtDesc(
            List.of(AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_PROGRESS),
            pageable
        );
    }

    @Transactional
    public void process(UUID requestId, UUID hrAdminId) {
        AdminRequest request = adminRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        AdminRequest previous = snapshot(request);
        ensureActionable(request, "processed");

        request.setStatus(AdminRequestStatus.PROCESSED);
        request.setResolvedAt(Instant.now());
        request.setResolvedById(hrAdminId);
        adminRequestRepository.save(request);

        auditLogService.log(hrAdminId, AuditAction.UPDATE, "admin_request",
            requestId, previous, request);

        notificationPublisher.publish(buildProcessedEvent(request));
    }

    @Transactional
    public void reject(UUID requestId, UUID hrAdminId, String reason) {
        AdminRequest request = adminRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        AdminRequest previous = snapshot(request);
        ensureActionable(request, "rejected");

        request.setStatus(AdminRequestStatus.REJECTED);
        request.setRejectionReason(normalizeReason(reason));
        request.setResolvedAt(Instant.now());
        request.setResolvedById(hrAdminId);
        adminRequestRepository.save(request);

        auditLogService.log(hrAdminId, AuditAction.REJECT, "admin_request",
            requestId, previous, request);

        notificationPublisher.publish(buildRejectedEvent(request));
    }

    @Transactional
    public void markInProgress(UUID requestId, UUID hrAdminId) {
        AdminRequest request = adminRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        if (request.getStatus() != AdminRequestStatus.SUBMITTED) {
            throw new InvalidAdminRequestStateException(
                "Admin request can only move to IN_PROGRESS from SUBMITTED");
        }

        AdminRequest previous = snapshot(request);
        request.setStatus(AdminRequestStatus.IN_PROGRESS);
        adminRequestRepository.save(request);

        auditLogService.log(hrAdminId, AuditAction.UPDATE, "admin_request",
            requestId, previous, request);
    }

    @Transactional
    public void cancel(UUID requestId, UUID requesterId) {
        AdminRequest request = adminRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        if (!request.getRequesterId().equals(requesterId)) {
            throw new AccessDeniedException("You can only cancel your own admin requests");
        }

        if (!isCancellable(request.getStatus())) {
            throw new InvalidAdminRequestStateException(
                "Admin request cannot be cancelled from status: " + request.getStatus());
        }

        AdminRequest previous = snapshot(request);
        request.setStatus(AdminRequestStatus.CANCELLED);
        request.setResolvedAt(Instant.now());
        request.setResolvedById(requesterId);
        adminRequestRepository.save(request);

        auditLogService.log(requesterId, AuditAction.UPDATE, "admin_request",
            requestId, previous, request);
    }

    private NotificationEvent buildSubmittedEvent(
            AdminRequest request,
            UUID requesterId,
            AdminRequestType type) {
        User user = userRepository.findById(requesterId).orElseThrow();

        Map<String, String> paramsMap = new LinkedHashMap<>();
        paramsMap.put("requesterName", user.getFirstName() + " " + user.getLastName());
        paramsMap.put("trackingNumber", request.getTrackingNumber());
        paramsMap.put("requestType", type.getName());
        paramsMap.put("linkPath", "/requests/inbox");
        String paramsJson = serializeMap(paramsMap);

        List<User> administrationUsers = userRepository.findByRole("ADMINISTRATION");
        List<User> hrAdmins = administrationUsers.isEmpty() ? userRepository.findByRole("HR_ADMIN") : administrationUsers;
        UUID targetUserId = hrAdmins.isEmpty() ? requesterId : hrAdmins.get(0).getId();

        return NotificationEvent.builder()
            .eventType(NotificationEventType.ADMIN_REQUEST_SUBMITTED)
            .targetUserId(targetUserId)
            .titleKey("admin.submitted.title")
            .bodyKey("admin.submitted.body")
            .params(paramsJson)
            .locale("fr")
            .routingKey("admin.submitted")
            .publishedAt(Instant.now())
            .build();
    }

    private NotificationEvent buildRejectedEvent(AdminRequest request) {
        User user = userRepository.findById(request.getRequesterId()).orElseThrow();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("trackingNumber", request.getTrackingNumber() == null ? "" : request.getTrackingNumber());
        params.put("rejectionReason", request.getRejectionReason() == null ? "" : request.getRejectionReason());
        params.put("linkPath", "/requests");

        String paramsJson = serializeMap(params);

        return NotificationEvent.builder()
            .eventType(NotificationEventType.ADMIN_REQUEST_REJECTED)
            .targetUserId(user.getId())
            .titleKey("admin.rejected.title")
            .bodyKey("admin.rejected.body")
            .params(paramsJson)
            .locale(user.getLocalePreference())
            .routingKey("admin.rejected")
            .publishedAt(Instant.now())
            .build();
    }

    private NotificationEvent buildProcessedEvent(AdminRequest request) {
        User user = userRepository.findById(request.getRequesterId()).orElseThrow();

        Map<String, String> paramsMap = new LinkedHashMap<>();
        paramsMap.put("trackingNumber", request.getTrackingNumber());
        paramsMap.put("linkPath", "/requests");
        String paramsJson = serializeMap(paramsMap);

        return NotificationEvent.builder()
            .eventType(NotificationEventType.ADMIN_REQUEST_PROCESSED)
            .targetUserId(user.getId())
            .titleKey("admin.processed.title")
            .bodyKey("admin.processed.body")
            .params(paramsJson)
            .locale(user.getLocalePreference())
            .routingKey("admin.processed")
            .publishedAt(Instant.now())
            .build();
    }

    private String serializeMap(Map<String, String> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (JsonProcessingException e) { throw new RuntimeException("Failed to serialize params", e); }
    }

    private boolean isActionable(AdminRequestStatus status) {
        return status == AdminRequestStatus.SUBMITTED || status == AdminRequestStatus.IN_PROGRESS;
    }

    private boolean isCancellable(AdminRequestStatus status) {
        return status == AdminRequestStatus.SUBMITTED || status == AdminRequestStatus.IN_PROGRESS;
    }

    private void ensureActionable(AdminRequest request, String targetAction) {
        if (!isActionable(request.getStatus())) {
            throw new InvalidAdminRequestStateException(
                "Admin request cannot be " + targetAction + " from status: " + request.getStatus());
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AdminRequest snapshot(AdminRequest request) {
        return AdminRequest.builder()
            .id(request.getId())
            .requesterId(request.getRequesterId())
            .requestTypeId(request.getRequestTypeId())
            .trackingNumber(request.getTrackingNumber())
            .description(request.getDescription())
            .urgencyLevel(request.getUrgencyLevel())
            .status(request.getStatus())
            .metadata(request.getMetadata())
            .rejectionReason(request.getRejectionReason())
            .submittedAt(request.getSubmittedAt())
            .resolvedAt(request.getResolvedAt())
            .resolvedById(request.getResolvedById())
            .build();
    }
}
