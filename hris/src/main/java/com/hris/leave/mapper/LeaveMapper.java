package com.hris.leave.mapper;

import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LeaveMapper {
    @Mapping(target = "leaveTypeCode", ignore = true)
    @Mapping(target = "leaveTypeName", ignore = true)
    LeaveRequestResponseDto toDto(LeaveRequest request);

    @Mapping(target = "leaveTypeCode", ignore = true)
    @Mapping(target = "leaveTypeName", ignore = true)
    @Mapping(target = "availableDays", expression = "java(balance.getAvailableDays())")
    LeaveBalanceDto toBalanceDto(LeaveBalance balance);
}
