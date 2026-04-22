package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeOnboardingServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private EmployeeService employeeService;
    @Mock private AccountProvisioningService accountProvisioningService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private EmployeeOnboardingService employeeOnboardingService;

    @Test
    @DisplayName("onboards employee with linked provisioned account")
    void onboardsEmployeeWithLinkedProvisionedAccount() {
        UUID actorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        EmployeeCreateDto dto = new EmployeeCreateDto(
            "yasmine.dev",
            "yasmine@demo.hris.local",
            "Yasmine",
            "Developer",
            "Temp123!",
            true,
            List.of(roleId),
            "EMP-900",
            LocalDate.of(2026, 4, 22),
            "Software Engineer",
            EmployeeStatus.ACTIVE,
            ContractType.PERMANENT,
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        User provisionedUser = User.builder()
            .id(userId)
            .keycloakId("kc-user-001")
            .email(dto.email())
            .firstName(dto.firstName())
            .lastName(dto.lastName())
            .isActive(true)
            .build();

        Employee savedEmployee = Employee.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .employeeCode(dto.employeeCode())
            .hireDate(dto.hireDate())
            .jobTitle(dto.jobTitle())
            .status(dto.status())
            .contractType(dto.contractType())
            .departmentId(dto.departmentId())
            .workScheduleId(dto.workScheduleId())
            .build();

        EmployeeResponseDto response = new EmployeeResponseDto(
            savedEmployee.getId(),
            userId,
            savedEmployee.getEmployeeCode(),
            savedEmployee.getHireDate(),
            savedEmployee.getJobTitle(),
            savedEmployee.getStatus(),
            savedEmployee.getContractType(),
            savedEmployee.getDepartmentId(),
            savedEmployee.getWorkScheduleId(),
            null
        );

        when(employeeRepository.findByEmployeeCode("EMP-900")).thenReturn(Optional.empty());
        when(accountProvisioningService.provision(any(AccountProvisioningRequest.class), eq(actorId))).thenReturn(provisionedUser);
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(employeeMapper.toDto(savedEmployee)).thenReturn(response);

        EmployeeResponseDto result = employeeOnboardingService.onboard(dto, actorId);

        assertThat(result.userId()).isEqualTo(userId);
        verify(employeeService).initializeLeaveBalancesForNewEmployee(savedEmployee.getId());
    }

    @Test
    @DisplayName("rolls back external account when employee save fails")
    void rollsBackExternalAccountWhenEmployeeSaveFails() {
        UUID actorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        EmployeeCreateDto dto = new EmployeeCreateDto(
            "yasmine.dev",
            "yasmine@demo.hris.local",
            "Yasmine",
            "Developer",
            "Temp123!",
            false,
            List.of(roleId),
            "EMP-900",
            LocalDate.of(2026, 4, 22),
            "Software Engineer",
            EmployeeStatus.ACTIVE,
            ContractType.PERMANENT,
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        when(employeeRepository.findByEmployeeCode("EMP-900")).thenReturn(Optional.empty());
        when(accountProvisioningService.provision(any(AccountProvisioningRequest.class), eq(actorId))).thenReturn(
            User.builder()
                .id(UUID.randomUUID())
                .keycloakId("kc-user-rollback")
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .isActive(true)
                .build()
        );
        doThrow(new IllegalStateException("Employee save failed"))
            .when(employeeRepository).save(any(Employee.class));

        assertThatThrownBy(() -> employeeOnboardingService.onboard(dto, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Employee save failed");

        verify(accountProvisioningService).rollbackExternalAccount("kc-user-rollback");
    }
}
