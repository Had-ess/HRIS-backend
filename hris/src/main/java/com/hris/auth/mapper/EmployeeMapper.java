package com.hris.auth.mapper;

import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.AccountStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface EmployeeMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "departmentId", source = "departmentId")
    @Mapping(target = "workScheduleId", source = "workScheduleId")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "accountStatus", expression = "java(computeAccountStatus(employee.getUser()))")
    EmployeeResponseDto toDto(Employee employee);

    default AccountStatus computeAccountStatus(User user) {
        if (user == null) return AccountStatus.ACTIVE;
        if (!user.isActive()) return AccountStatus.INACTIVE;
        if (user.isSeed()) return AccountStatus.PENDING_ACTIVATION;
        return AccountStatus.ACTIVE;
    }
}
