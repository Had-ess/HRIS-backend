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
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.LocalAccountService;
import com.hris.lifecycle.dto.LifecycleDtos.CreateContractRequest;
import com.hris.lifecycle.service.EmployeeContractService;
import com.hris.recruitment.service.NewHireHandoffService;
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
    private final EmployeeContractService employeeContractService;
    private final AccountProvisioningService accountProvisioningService;
    private final LocalAccountService localAccountService;
    private final AuditLogService auditLogService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final EmployeeHistoryService employeeHistoryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NewHireHandoffService newHireHandoffService;

    @Transactional
    public EmployeeResponseDto onboard(EmployeeCreateDto dto, UUID actorId) {
        if (employeeRepository.findByEmployeeCode(dto.employeeCode().trim()).isPresent()) {
            throw new IllegalStateException("Employee code must be unique");
        }
        if (dto.supervisorEmployeeId() != null) {
            employeeService.validateSupervisorAssignment(null, dto.supervisorEmployeeId());
        }
        var jobTitle = employeeService.resolveActiveJobTitle(dto.jobTitleId());

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
        // New hires always start ACTIVE: lifecycle transitions (termination,
        // deactivation) own every other status and their side effects.
        Employee saved = employeeRepository.save(Employee.builder()
            .userId(user.getId())
            .employeeCode(dto.employeeCode().trim())
            .hireDate(dto.hireDate())
            .jobTitle(jobTitle.getName())
            .jobTitleId(jobTitle.getId())
            .status(EmployeeStatus.ACTIVE)
            .contractType(dto.contractType())
            .departmentId(dto.departmentId())
            .supervisorEmployeeId(dto.supervisorEmployeeId())
            .workScheduleId(dto.workScheduleId())
            .location(dto.location() != null && !dto.location().isBlank() ? dto.location().trim() : null)
            .cin(dto.cin() != null && !dto.cin().isBlank() ? dto.cin().trim() : null)
            .build());

        employeeHistoryService.recordHire(saved, actorId);
        // The contract table is the source of truth for employment terms — every
        // employee gets an initial ACTIVE contract starting on the hire date.
        employeeContractService.createContract(saved.getId(), new CreateContractRequest(
            dto.contractType(), dto.hireDate(), dto.contractEndDate(), dto.probationEndDate(), null), actorId);
        employeeService.initializeLeaveBalancesForNewEmployee(saved.getId());
        analyticsEventPublisher.publishEmployeeHireEvent(saved);
        auditLogService.log(actorId, AuditAction.CREATE, "employee", saved.getId(), null, saved);
        applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
            StructuralEventType.EMPLOYEE_ONBOARDED, user.getId(), saved.getId(), actorId));
        // Close the recruitment loop when this employee was created from a hire handoff:
        // links the employee, increments the requisition's filled count, auto-FILLED when
        // full. Shares this transaction, so a failed handoff completion rolls onboarding back.
        if (dto.newHireId() != null) {
            newHireHandoffService.complete(dto.newHireId(), saved.getId(), actorId);
        }
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
