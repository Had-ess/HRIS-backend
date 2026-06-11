package com.hris.auth.service;

import com.hris.access.enums.StructuralEventType;
import com.hris.access.event.StructuralChangeEvent;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.LocalAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeOnboardingService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmployeeMapper employeeMapper;
    private final EmployeeService employeeService;
    private final AccountProvisioningService accountProvisioningService;
    private final LocalAccountService localAccountService;
    private final AuditLogService auditLogService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final EmployeeHistoryService employeeHistoryService;
    private final ApplicationEventPublisher applicationEventPublisher;

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
            dto.profileIds()
        ), actorId);

        // User, employee, profiles, and activation token share this transaction:
        // any failure rolls back everything (no external-account compensation
        // needed since the owned-auth migration).
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
            .location(dto.location() != null && !dto.location().isBlank() ? dto.location().trim() : null)
            .cin(dto.cin() != null && !dto.cin().isBlank() ? dto.cin().trim() : null)
            .build());

        employeeHistoryService.recordHire(saved, actorId);
        employeeService.initializeLeaveBalancesForNewEmployee(saved.getId());
        analyticsEventPublisher.publishEmployeeHireEvent(saved);
        auditLogService.log(actorId, AuditAction.CREATE, "employee", saved.getId(), null, saved);
        applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
            StructuralEventType.EMPLOYEE_ONBOARDED, user.getId(), saved.getId(), actorId));
        return employeeMapper.toDto(saved);
    }

    @Transactional
    public void resendActivationEmail(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        User user = userRepository.findById(employee.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("User not found for employee"));
        if (localAccountService.isActivated(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ALREADY_ACTIVE");
        }
        localAccountService.initiateActivation(user);
    }
}
