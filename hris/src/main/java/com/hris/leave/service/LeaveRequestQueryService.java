package com.hris.leave.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestQueryService {

    private final LeaveTypeService leaveTypeService;
    private final ApprovalViewService approvalViewService;
    private final LeaveRequestService leaveRequestService;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public LeaveRequestResponseDto toDto(LeaveRequest request, UUID requesterId) {
        return toDto(
            request,
            leaveTypeService.getDtoById(request.getLeaveTypeId()),
            employeeRepository.findById(request.getEmployeeId()).orElse(null),
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

        Map<UUID, Employee> employeesById = employeeRepository
            .findAllById(requests.getContent().stream()
                .map(LeaveRequest::getEmployeeId)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(Employee::getId, Function.identity()));

        return requests.map(request -> toDto(
            request,
            leaveTypesById.get(request.getLeaveTypeId()),
            employeesById.get(request.getEmployeeId()),
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
            Employee employee,
            UUID requesterId,
            List<ApprovalStepResponseDto> approvalSteps) {
        var user = Optional.ofNullable(employee).map(Employee::getUser).orElse(null);
        var department = Optional.ofNullable(employee).map(Employee::getDepartment).orElse(null);
        return new LeaveRequestResponseDto(
            request.getId(),
            formatReference(request),
            request.getEmployeeId(),
            employee != null ? employee.getEmployeeCode() : null,
            user != null ? user.getFirstName() : null,
            user != null ? user.getLastName() : null,
            employee != null ? employee.getJobTitle() : null,
            department != null ? department.getName() : null,
            request.getLeaveTypeId(),
            leaveType != null ? leaveType.code() : null,
            leaveType != null ? leaveType.name() : null,
            request.getStartDate(),
            request.getEndDate(),
            request.getWorkingDays(),
            request.getDurationDays(),
            request.getDurationHours(),
            request.getStartTime(),
            request.getEndTime(),
            request.getPartialMode(),
            request.getUrgencyLevel(),
            request.getStatus(),
            request.getComment(),
            request.getSubmittedAt(),
            leaveRequestService.canUploadAttachment(request, requesterId),
            request.isHalfDay(),
            null,
            approvalSteps
        );
    }

    private String formatReference(LeaveRequest request) {
        int year = request.getSubmittedAt() != null
            ? java.time.LocalDateTime.ofInstant(request.getSubmittedAt(), java.time.ZoneOffset.UTC).getYear()
            : request.getStartDate().getYear();
        String suffix = request.getId() != null
            ? request.getId().toString().replace("-", "").substring(0, 6).toUpperCase()
            : "DRAFT";
        return "LV-" + year + "-" + suffix;
    }
}
