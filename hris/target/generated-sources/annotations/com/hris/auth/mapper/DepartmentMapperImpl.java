package com.hris.auth.mapper;

import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T18:12:20+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class DepartmentMapperImpl implements DepartmentMapper {

    @Override
    public DepartmentDto toDto(Department department) {
        if ( department == null ) {
            return null;
        }

        boolean isActive = false;
        UUID id = null;
        String name = null;
        String code = null;
        UUID headEmployeeId = null;

        isActive = department.isActive();
        id = department.getId();
        name = department.getName();
        code = department.getCode();
        headEmployeeId = department.getHeadEmployeeId();

        DepartmentDto departmentDto = new DepartmentDto( id, name, code, headEmployeeId, isActive );

        return departmentDto;
    }
}
