package com.hris.admin.service;

import com.hris.admin.dto.AdminRequestAttachmentDto;
import com.hris.admin.dto.AdminRequestCommentDto;
import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestStatusHistoryDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestAttachment;
import com.hris.admin.entity.AdminRequestComment;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestAttachmentRepository;
import com.hris.admin.repository.AdminRequestCommentRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.auth.service.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestQueryService {

    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final AdminRequestAttachmentRepository attachmentRepository;
    private final AdminRequestCommentRepository commentRepository;
    private final UserDisplayNameService userDisplayNameService;

    @Transactional(readOnly = true)
    public Page<AdminRequestResponseDto> toDtoPage(Page<AdminRequest> requests, boolean includeInternalComments) {
        Map<UUID, AdminRequestType> types = resolveTypes(
            requests.getContent().stream().map(AdminRequest::getTypeId).toList());
        Map<UUID, String> userNames = resolveUserNames(requests.getContent());
        return requests.map(request -> toDto(request, includeInternalComments, types, userNames));
    }

    @Transactional(readOnly = true)
    public AdminRequestResponseDto toDto(AdminRequest request, boolean includeInternalComments) {
        return toDto(
            request,
            includeInternalComments,
            resolveTypes(List.of(request.getTypeId())),
            resolveUserNames(List.of(request))
        );
    }

    private AdminRequestResponseDto toDto(
            AdminRequest request,
            boolean includeInternalComments,
            Map<UUID, AdminRequestType> types,
            Map<UUID, String> userNames) {
        List<AdminRequestAttachmentDto> attachments = attachmentRepository
            .findByAdminRequestIdOrderByUploadedAtAsc(request.getId())
            .stream()
            .map(attachment -> toAttachmentDto(attachment, userNames))
            .toList();

        List<AdminRequestCommentDto> comments = commentRepository
            .findByAdminRequestIdOrderByCreatedAtAsc(request.getId())
            .stream()
            .filter(comment -> includeInternalComments || !comment.isInternal())
            .map(comment -> toCommentDto(comment, userNames))
            .toList();

        AdminRequestType type = types.get(request.getTypeId());
        return new AdminRequestResponseDto(
            request.getId(),
            request.getRequestNumber(),
            request.getRequesterEmployeeId(),
            request.getRequesterUserId(),
            userNames.get(request.getRequesterUserId()),
            request.getTypeId(),
            type != null ? type.getCode() : null,
            type != null ? type.getName() : null,
            request.getSubject(),
            request.getDescription(),
            request.getStatus(),
            request.getSubmittedAt(),
            request.getReviewedAt(),
            request.getDecidedAt(),
            request.getCompletedAt(),
            request.getDueAt(),
            isOverdue(request),
            processingDurationMinutes(request),
            request.getProcessedByUserId(),
            userNames.get(request.getProcessedByUserId()),
            request.getRejectionReason(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            attachments,
            comments,
            buildStatusHistory(request)
        );
    }

    private AdminRequestAttachmentDto toAttachmentDto(AdminRequestAttachment attachment, Map<UUID, String> userNames) {
        return new AdminRequestAttachmentDto(
            attachment.getId(),
            attachment.getFileName(),
            attachment.getContentType(),
            attachment.getSizeBytes(),
            attachment.isResponseDocument(),
            attachment.getUploadedByUserId(),
            userNames.get(attachment.getUploadedByUserId()),
            attachment.getUploadedAt()
        );
    }

    private AdminRequestCommentDto toCommentDto(AdminRequestComment comment, Map<UUID, String> userNames) {
        return new AdminRequestCommentDto(
            comment.getId(),
            comment.getAuthorUserId(),
            userNames.get(comment.getAuthorUserId()),
            comment.getComment(),
            comment.isInternal(),
            comment.getCreatedAt()
        );
    }

    private Map<UUID, AdminRequestType> resolveTypes(Collection<UUID> typeIds) {
        return adminRequestTypeRepository.findAllById(typeIds).stream()
            .collect(java.util.stream.Collectors.toMap(AdminRequestType::getId, t -> t));
    }

    private Map<UUID, String> resolveUserNames(Collection<AdminRequest> requests) {
        List<UUID> userIds = new ArrayList<>();
        for (AdminRequest request : requests) {
            userIds.add(request.getRequesterUserId());
            userIds.add(request.getProcessedByUserId());
        }
        for (AdminRequest request : requests) {
            attachmentRepository.findByAdminRequestIdOrderByUploadedAtAsc(request.getId())
                .forEach(attachment -> userIds.add(attachment.getUploadedByUserId()));
            commentRepository.findByAdminRequestIdOrderByCreatedAtAsc(request.getId())
                .forEach(comment -> userIds.add(comment.getAuthorUserId()));
        }
        return userDisplayNameService.resolveDisplayNames(userIds);
    }

    private boolean isOverdue(AdminRequest request) {
        if (request.getDueAt() == null) {
            return false;
        }
        return switch (request.getStatus()) {
            case SUBMITTED, IN_REVIEW, APPROVED -> request.getDueAt().isBefore(Instant.now());
            default -> false;
        };
    }

    private Long processingDurationMinutes(AdminRequest request) {
        if (request.getSubmittedAt() == null) {
            return null;
        }
        Instant end = request.getCompletedAt() != null
            ? request.getCompletedAt()
            : request.getDecidedAt() != null
                ? request.getDecidedAt()
                : Instant.now();
        return Duration.between(request.getSubmittedAt(), end).toMinutes();
    }

    private List<AdminRequestStatusHistoryDto> buildStatusHistory(AdminRequest request) {
        List<AdminRequestStatusHistoryDto> history = new ArrayList<>();
        history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.DRAFT, request.getCreatedAt()));
        if (request.getSubmittedAt() != null) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.SUBMITTED, request.getSubmittedAt()));
        }
        if (request.getReviewedAt() != null) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.IN_REVIEW, request.getReviewedAt()));
        }
        if (request.getStatus() == AdminRequestStatus.APPROVED && request.getDecidedAt() != null) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.APPROVED, request.getDecidedAt()));
        }
        if (request.getStatus() == AdminRequestStatus.REJECTED && request.getDecidedAt() != null) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.REJECTED, request.getDecidedAt()));
        }
        if (request.getCompletedAt() != null) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.COMPLETED, request.getCompletedAt()));
        }
        if (request.getStatus() == AdminRequestStatus.CANCELLED) {
            history.add(new AdminRequestStatusHistoryDto(AdminRequestStatus.CANCELLED, request.getUpdatedAt()));
        }
        return history.stream().filter(item -> item.occurredAt() != null).toList();
    }
}
