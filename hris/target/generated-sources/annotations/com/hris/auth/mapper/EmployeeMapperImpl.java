package com.hris.auth.mapper;

import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-18T14:16:51+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class EmployeeMapperImpl implements EmployeeMapper {

    @Autowired
    private UserMapper userMapper;

    @Override
    public EmployeeResponseDto toDto(Employee employee) {
        if ( employee == null ) {
            return null;
        }

        UUID userId = null;
        UUID departmentId = null;
        UUID workScheduleId = null;
        UserResponseDto user = null;
        UUID id = null;
        String employeeCode = null;
        LocalDate hireDate = null;
        String jobTitle = null;
        EmployeeStatus status = null;
        ContractType contractType = null;

        userId = employeeUserId( employee );
        departmentId = employee.getDepartmentId();
        workScheduleId = employee.getWorkScheduleId();
        user = userMapper.toDto( employee.getUser() );
        id = employee.getId();
        employeeCode = employee.getEmployeeCode();
        hireDate = employee.getHireDate();
        jobTitle = employee.getJobTitle();
        status = employee.getStatus();
        contractType = employee.getContractType();

        EmployeeResponseDto employeeResponseDto = new EmployeeResponseDto( id, userId, employeeCode, hireDate, jobTitle, status, contractType, departmentId, workScheduleId, user );

        return employeeResponseDto;
    }

    @Override
    public Employee toEntity(EmployeeCreateDto dto) {
        if ( dto == null ) {
            return null;
        }

        Employee.EmployeeBuilder employee = Employee.builder();

        employee.userId( dto.userId() );
        employee.employeeCode( dto.employeeCode() );
        employee.hireDate( dto.hireDate() );
        employee.jobTitle( dto.jobTitle() );
        employee.status( dto.status() );
        employee.contractType( dto.contractType() );
        employee.departmentId( dto.departmentId() );
        employee.workScheduleId( dto.workScheduleId() );

        return employee.build();
    }

    private UUID employeeUserId(Employee employee) {
        User user = employee.getUser();
        if ( user == null ) {
            return null;
        }
        return user.getId();
    }
}
