package com.hris.auth.mapper;

import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    @Mapping(target = "isActive", source = "active")
    DepartmentDto toDto(Department department);
}
