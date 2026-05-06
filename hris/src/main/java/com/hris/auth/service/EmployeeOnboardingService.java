package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeOnboardingService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;
    private final EmployeeService employeeService;
    private final AccountProvisioningService accountProvisioningService;
    private final EmployeeOnboardingEmailService employeeOnboardingEmailService;
    private final AuditLogService auditLogService;

    @Transactional
    public EmployeeResponseDto onboard(EmployeeCreateDto dto, UUID actorId) {
        if (employeeRepository.findByEmployeeCode(dto.employeeCode().trim()).isPresent()) {
            throw new IllegalStateException("Employee code must be unique");
        }
        if (dto.supervisorEmployeeId() != null
            && employeeRepository.findById(dto.supervisorEmployeeId()).isEmpty()) {
            throw new EntityNotFoundException("Supervisor employee not found");
        }

        User user = accountProvisioningService.provision(new AccountProvisioningRequest(
            dto.username(),
            dto.email(),
            dto.firstName(),
            dto.lastName(),
            dto.password(),
            dto.temporaryPassword() != null && dto.temporaryPassword(),
            dto.roleIds()
        ), actorId);

        try {
            Employee saved = employeeRepository.save(Employee.builder()
                .userId(user.getId())
                .employeeCode(dto.employeeCode().trim())
                .hireDate(dto.hireDate())
                .jobTitle(dto.jobTitle().trim())
                .status(dto.status())
                .contractType(dto.contractType())
                .departmentId(dto.departmentId())
                .supervisorEmployeeId(dto.supervisorEmployeeId())
                .workScheduleId(dto.workScheduleId())
                .build());

            employeeService.initializeLeaveBalancesForNewEmployee(saved.getId());
            auditLogService.log(actorId, AuditAction.CREATE, "employee", saved.getId(), null, saved);
            employeeOnboardingEmailService.sendCredentials(
                dto.email().trim(),
                dto.firstName().trim(),
                dto.username().trim(),
                dto.password(),
                dto.temporaryPassword() != null && dto.temporaryPassword()
            );
            return employeeMapper.toDto(saved);
        } catch (RuntimeException ex) {
            accountProvisioningService.rollbackExternalAccount(user.getKeycloakId());
            throw ex;
        }
    }
}
