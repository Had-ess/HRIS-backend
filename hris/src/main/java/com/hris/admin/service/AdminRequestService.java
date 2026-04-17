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
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

        notificationPublisher.publish(buildSubmittedEvent(saved, requesterId));

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
        AdminRequest request = adminRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        if (!isActionable(request.getStatus())) {
            throw new IllegalStateException(
                "Cannot process an admin request in status: " + request.getStatus());
        }

        request.setStatus(AdminRequestStatus.PROCESSED);
        request.setResolvedAt(Instant.now());
        request.setResolvedById(hrAdminId);
        adminRequestRepository.save(request);

        auditLogService.log(hrAdminId, AuditAction.UPDATE, "admin_request",
            requestId, null, request);

        notificationPublisher.publish(buildProcessedEvent(request));
    }

    @Transactional
    public void reject(UUID requestId, UUID hrAdminId) {
        AdminRequest request = adminRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Admin request not found"));

        if (!isActionable(request.getStatus())) {
            throw new IllegalStateException(
                "Cannot reject an admin request in status: " + request.getStatus());
        }

        request.setStatus(AdminRequestStatus.REJECTED);
        request.setResolvedAt(Instant.now());
        request.setResolvedById(hrAdminId);
        adminRequestRepository.save(request);

        auditLogService.log(hrAdminId, AuditAction.REJECT, "admin_request",
            requestId, null, request);
    }

    private NotificationEvent buildSubmittedEvent(AdminRequest request, UUID requesterId) {
        User user = userRepository.findById(requesterId).orElseThrow();
        AdminRequestType type = adminRequestTypeRepository.findById(request.getRequestTypeId()).orElseThrow();

        String paramsJson = serializeMap(Map.of(
            "requesterName", user.getFirstName() + " " + user.getLastName(),
            "trackingNumber", request.getTrackingNumber(),
            "requestType", type.getName()
        ));

        List<User> hrAdmins = userRepository.findByRole("HR_ADMIN");
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

    private NotificationEvent buildProcessedEvent(AdminRequest request) {
        User user = userRepository.findById(request.getRequesterId()).orElseThrow();

        String paramsJson = serializeMap(Map.of("trackingNumber", request.getTrackingNumber()));

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
}
