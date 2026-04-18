package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.EmployeeUpdateDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeavePolicy;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> getAll(Pageable pageable) {
        return employeeRepository.findAll(pageable).map(employeeMapper::toDto);
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDto getById(UUID id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        return employeeMapper.toDto(employee);
    }

    @Transactional
    public EmployeeResponseDto create(EmployeeCreateDto dto, UUID actorId) {
        Employee employee = employeeMapper.toEntity(dto);
        Employee saved = employeeRepository.save(employee);

        initializeLeaveBalancesForNewEmployee(saved.getId());

        auditLogService.log(actorId, AuditAction.CREATE, "employee",
            saved.getId(), null, saved);

        return employeeMapper.toDto(saved);
    }

    @Transactional
    public EmployeeResponseDto update(UUID id, EmployeeUpdateDto dto, UUID actorId) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Employee previous = Employee.builder()
            .id(employee.getId())
            .userId(employee.getUserId())
            .employeeCode(employee.getEmployeeCode())
            .hireDate(employee.getHireDate())
            .jobTitle(employee.getJobTitle())
            .status(employee.getStatus())
            .contractType(employee.getContractType())
            .departmentId(employee.getDepartmentId())
            .workScheduleId(employee.getWorkScheduleId())
            .build();

        if (dto.employeeCode() != null) {
            employee.setEmployeeCode(dto.employeeCode());
        }
        if (dto.hireDate() != null) {
            employee.setHireDate(dto.hireDate());
        }
        if (dto.jobTitle() != null) {
            employee.setJobTitle(dto.jobTitle());
        }
        if (dto.status() != null) {
            employee.setStatus(dto.status());
        }
        if (dto.contractType() != null) {
            employee.setContractType(dto.contractType());
        }
        if (dto.departmentId() != null) {
            employee.setDepartmentId(dto.departmentId());
        }
        if (dto.workScheduleId() != null) {
            employee.setWorkScheduleId(dto.workScheduleId());
        }

        Employee saved = employeeRepository.save(employee);

        auditLogService.log(actorId, AuditAction.UPDATE, "employee",
            saved.getId(), previous, saved);

        return employeeMapper.toDto(saved);
    }

    @Transactional
    public void initializeLeaveBalancesForNewEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        int currentYear = LocalDate.now().getYear();
        int seniorityYears = Period.between(employee.getHireDate(), LocalDate.now()).getYears();

        List<LeaveType> activeTypes = leaveTypeRepository.findByIsActiveTrue();

        for (LeaveType type : activeTypes) {
            Optional<LeavePolicy> policy = leavePolicyRepository.findApplicablePolicy(
                type.getId(), employee.getContractType(), seniorityYears);

            if (policy.isPresent()) {
                LeaveBalance balance = LeaveBalance.builder()
                    .employeeId(employeeId)
                    .leaveTypeId(type.getId())
                    .year(currentYear)
                    .totalDays(policy.get().getMaxDaysPerYear())
                    .usedDays(0)
                    .pendingDays(0)
                    .carryOverDays(0)
                    .build();
                leaveBalanceRepository.save(balance);
            }
        }

        auditLogService.log(employee.getUserId(), AuditAction.CREATE,
            "leave_balance_initialization", employeeId, null, null);
    }
}
