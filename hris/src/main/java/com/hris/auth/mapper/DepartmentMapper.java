package com.hris.auth.mapper;

import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    default DepartmentDto toDto(Department department) {
        if (department == null) {
            return null;
        }

        return new DepartmentDto(
            department.getId(),
            department.getName(),
            department.getCode(),
            department.getHeadEmployeeId(),
            department.isActive(),
            0L,
            0L,
            0L,
            department.getOpenings()
        );
    }
}
