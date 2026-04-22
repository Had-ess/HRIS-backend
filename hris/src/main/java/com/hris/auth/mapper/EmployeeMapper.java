package com.hris.auth.mapper;

import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface EmployeeMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "departmentId", source = "departmentId")
    @Mapping(target = "workScheduleId", source = "workScheduleId")
    @Mapping(target = "user", source = "user")
    EmployeeResponseDto toDto(Employee employee);
}
