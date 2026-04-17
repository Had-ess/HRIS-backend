package com.hris.leave.mapper;

import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T19:19:05+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class LeaveMapperImpl implements LeaveMapper {

    @Override
    public LeaveRequestResponseDto toDto(LeaveRequest request) {
        if ( request == null ) {
            return null;
        }

        UUID id = null;
        UUID employeeId = null;
        UUID leaveTypeId = null;
        LocalDate startDate = null;
        LocalDate endDate = null;
        int workingDays = 0;
        UrgencyLevel urgencyLevel = null;
        LeaveStatus status = null;
        String comment = null;
        Instant submittedAt = null;

        id = request.getId();
        employeeId = request.getEmployeeId();
        leaveTypeId = request.getLeaveTypeId();
        startDate = request.getStartDate();
        endDate = request.getEndDate();
        workingDays = request.getWorkingDays();
        urgencyLevel = request.getUrgencyLevel();
        status = request.getStatus();
        comment = request.getComment();
        submittedAt = request.getSubmittedAt();

        LeaveRequestResponseDto leaveRequestResponseDto = new LeaveRequestResponseDto( id, employeeId, leaveTypeId, startDate, endDate, workingDays, urgencyLevel, status, comment, submittedAt );

        return leaveRequestResponseDto;
    }

    @Override
    public LeaveBalanceDto toBalanceDto(LeaveBalance balance) {
        if ( balance == null ) {
            return null;
        }

        UUID id = null;
        UUID employeeId = null;
        UUID leaveTypeId = null;
        int year = 0;
        int totalDays = 0;
        int usedDays = 0;
        int pendingDays = 0;
        int carryOverDays = 0;

        id = balance.getId();
        employeeId = balance.getEmployeeId();
        leaveTypeId = balance.getLeaveTypeId();
        year = balance.getYear();
        totalDays = balance.getTotalDays();
        usedDays = balance.getUsedDays();
        pendingDays = balance.getPendingDays();
        carryOverDays = balance.getCarryOverDays();

        int availableDays = balance.getAvailableDays();

        LeaveBalanceDto leaveBalanceDto = new LeaveBalanceDto( id, employeeId, leaveTypeId, year, totalDays, usedDays, pendingDays, carryOverDays, availableDays );

        return leaveBalanceDto;
    }
}
