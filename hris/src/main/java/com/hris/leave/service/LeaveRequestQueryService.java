package com.hris.leave.service;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.service.ApprovalViewService;
import com.hris.leave.dto.FileAttachmentDto;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.entity.FileAttachment;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.dto.LeaveTypeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestQueryService {

    private final LeaveTypeService leaveTypeService;
    private final ApprovalViewService approvalViewService;
    private final LeaveRequestService leaveRequestService;

    @Transactional(readOnly = true)
    public LeaveRequestResponseDto toDto(LeaveRequest request, UUID requesterId) {
        return toDto(
            request,
            leaveTypeService.getDtoById(request.getLeaveTypeId()),
            requesterId,
            approvalViewService.getStepsForSubject("LEAVE", request.getId())
        );
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponseDto> toDtoPage(Page<LeaveRequest> requests, UUID requesterId) {
        Map<UUID, LeaveTypeDto> leaveTypesById = requests.getContent().stream()
            .map(LeaveRequest::getLeaveTypeId)
            .distinct()
            .map(leaveTypeService::getDtoById)
            .filter(dto -> dto != null)
            .collect(Collectors.toMap(LeaveTypeDto::id, Function.identity()));

        Map<UUID, List<ApprovalStepResponseDto>> approvalStepsByRequestId = approvalViewService
            .getStepsForSubjects("LEAVE", requests.getContent().stream()
                .map(LeaveRequest::getId)
                .toList());

        return requests.map(request -> toDto(
            request,
            leaveTypesById.get(request.getLeaveTypeId()),
            requesterId,
            approvalStepsByRequestId.getOrDefault(request.getId(), List.of())
        ));
    }

    @Transactional(readOnly = true)
    public FileAttachmentDto toAttachmentDto(FileAttachment attachment) {
        return new FileAttachmentDto(
            attachment.getId(),
            attachment.getRequestId(),
            attachment.getFileName(),
            attachment.getMimeType(),
            attachment.getUploadedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<FileAttachmentDto> toAttachmentDtos(List<FileAttachment> attachments) {
        return attachments.stream()
            .map(this::toAttachmentDto)
            .toList();
    }

    private LeaveRequestResponseDto toDto(
            LeaveRequest request,
            LeaveTypeDto leaveType,
            UUID requesterId,
            List<ApprovalStepResponseDto> approvalSteps) {
        return new LeaveRequestResponseDto(
            request.getId(),
            request.getEmployeeId(),
            request.getLeaveTypeId(),
            leaveType != null ? leaveType.code() : null,
            leaveType != null ? leaveType.name() : null,
            request.getStartDate(),
            request.getEndDate(),
            request.getWorkingDays(),
            request.getUrgencyLevel(),
            request.getStatus(),
            request.getComment(),
            request.getSubmittedAt(),
            leaveRequestService.canUploadAttachment(request, requesterId),
            approvalSteps
        );
    }
}
