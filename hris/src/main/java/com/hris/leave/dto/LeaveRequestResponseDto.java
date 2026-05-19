package com.hris.leave.dto;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.leave.enums.PartialLeaveMode;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record LeaveRequestResponseDto(
    UUID id, String reference,
    UUID employeeId, String employeeCode, String employeeFirstName, String employeeLastName,
    String employeeJobTitle, String employeeDepartmentName,
    UUID leaveTypeId, String leaveTypeCode, String leaveTypeName,
    LocalDate startDate, LocalDate endDate, int workingDays,
    BigDecimal durationDays, BigDecimal durationHours, LocalTime startTime, LocalTime endTime, PartialLeaveMode partialMode,
    UrgencyLevel urgencyLevel, LeaveStatus status,
    String comment, Instant submittedAt, boolean canUploadAttachment,
    boolean isHalfDay, Double balanceAfter,
    List<ApprovalStepResponseDto> approvalSteps
) {
    public LeaveRequestResponseDto(
        UUID id, UUID employeeId, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName,
        LocalDate startDate, LocalDate endDate, int workingDays,
        BigDecimal durationDays, BigDecimal durationHours, LocalTime startTime, LocalTime endTime, PartialLeaveMode partialMode,
        UrgencyLevel urgencyLevel, LeaveStatus status,
        String comment, Instant submittedAt, boolean canUploadAttachment,
        boolean isHalfDay,
        List<ApprovalStepResponseDto> approvalSteps
    ) {
        this(
            id, null,
            employeeId, null, null, null, null, null,
            leaveTypeId, leaveTypeCode, leaveTypeName,
            startDate, endDate, workingDays, durationDays, durationHours, startTime, endTime, partialMode,
            urgencyLevel, status,
            comment, submittedAt, canUploadAttachment,
            isHalfDay, null,
            approvalSteps
        );
    }
}
