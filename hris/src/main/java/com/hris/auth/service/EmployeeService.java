package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.access.service.AccessResolutionService;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.EmployeeUpdateDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.AccountStatus;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeavePolicy;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeDepartmentHistoryRepository employeeDepartmentHistoryRepository;
    private final EmployeeStatusHistoryRepository employeeStatusHistoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final UserDeletionService userDeletionService;
    private final AuditLogService auditLogService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final EmployeeHistoryService employeeHistoryService;
    private final AccessScopeService accessScopeService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> getAll(UUID requesterId, Pageable pageable) {
        EmployeeReadScope scope = resolveReadScope(requesterId);
        return switch (scope.type()) {
            case GLOBAL -> employeeRepository.findAll(pageable).map(employeeMapper::toDto);
            case DEPARTMENT -> {
                if (scope.departmentId() == null) {
                    yield Page.empty(pageable);
                }
                yield employeeRepository.findByDepartmentId(scope.departmentId(), pageable).map(employeeMapper::toDto);
            }
            case DEPARTMENTS -> {
            if (scope.departmentIds().isEmpty()) {
                    yield Page.empty(pageable);
                }
                yield employeeRepository.findByDepartmentIdIn(scope.departmentIds(), pageable).map(employeeMapper::toDto);
            }
            case SELF -> {
                Employee requester = accessScopeService.findEmployee(requesterId).orElse(null);
                if (requester == null) {
                    yield Page.empty(pageable);
                }
                yield new PageImpl<>(List.of(employeeMapper.toDto(requester)), pageable, 1);
            }
        };
    }

    /**
     * Returns a compact profile summary for the employee hero banner.
     * GET /api/employees/{id}/profile-summary
     */
    @Transactional(readOnly = true)
    public com.hris.auth.dto.EmployeeProfileSummaryDto getProfileSummary(UUID employeeId, UUID requesterId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        // Department name
        String deptName = employee.getDepartmentId() != null
            ? departmentRepository.findById(employee.getDepartmentId()).map(d -> d.getName()).orElse(null)
            : null;

        // Supervisor name
        String supervisorName = employee.getSupervisorEmployeeId() != null
            ? employeeRepository.findById(employee.getSupervisorEmployeeId())
                .map(sup -> {
                    var u = sup.getUserId() != null
                        ? com.hris.auth.entity.User.class.cast(null) // resolved below
                        : null;
                    return sup.getEmployeeCode();
                }).orElse(null)
            : null;

        // Leave balances (current year, max 6)
        int year = LocalDate.now().getYear();
        List<com.hris.leave.dto.LeaveBalanceDto> balances = leaveBalanceRepository
            .findByEmployeeIdAndYear(employeeId, year).stream()
            .limit(6)
            .map(b -> {
                LeaveType lt = leaveTypeRepository.findById(b.getLeaveTypeId()).orElse(null);
                return new com.hris.leave.dto.LeaveBalanceDto(
                    b.getId(), b.getEmployeeId(), b.getLeaveTypeId(),
                    lt != null ? lt.getCode() : null, lt != null ? lt.getName() : null,
                    b.getYear(), b.getTotalDays(), b.getUsedDays(), b.getPendingDays(),
                    b.getCarryOverDays(), b.getAvailableDays()
                );
            })
            .toList();

        // Years of service (1 decimal)
        double yearsOfService = 0;
        if (employee.getHireDate() != null) {
            Period p = employee.getHireDate().until(LocalDate.now());
            yearsOfService = Math.round((p.getYears() + p.getMonths() / 12.0) * 10.0) / 10.0;
        }

        // Direct report count
        int directReportCount = employeeRepository.findBySupervisorEmployeeId(employeeId).size();

        // User fields - employee.user relationship may not be eagerly loaded; use userId lookup
        String firstName = null, lastName = null, email = null;
        if (employee.getUserId() != null) {
            var user = userRepository.findById(employee.getUserId()).orElse(null);
            if (user != null) {
                firstName = user.getFirstName();
                lastName = user.getLastName();
                email = user.getEmail();
            }
        }

        var user2 = employee.getUserId() != null ? userRepository.findById(employee.getUserId()).orElse(null) : null;
        AccountStatus accountStatus = user2 == null ? AccountStatus.ACTIVE
            : !user2.isActive() ? AccountStatus.INACTIVE
            : user2.isSeed() ? AccountStatus.PENDING_ACTIVATION
            : AccountStatus.ACTIVE;

        return new com.hris.auth.dto.EmployeeProfileSummaryDto(
            employee.getId(), employee.getUserId(),
            employee.getEmployeeCode(), firstName, lastName, email,
            employee.getJobTitle(), deptName, supervisorName,
            employee.getHireDate(), employee.getStatus(), accountStatus,
            employee.getContractType(), employee.getLocation(),
            yearsOfService, balances, directReportCount
        );
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDto getById(UUID id, UUID requesterId) {
        EmployeeReadScope scope = resolveReadScope(requesterId);
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (scope.type() == EmployeeReadScopeType.DEPARTMENT
            && scope.departmentId() != null
            && !scope.departmentId().equals(employee.getDepartmentId())) {
            throw new AccessDeniedException("You are not allowed to access this employee");
        }
        if (scope.type() == EmployeeReadScopeType.DEPARTMENTS
            && !scope.departmentIds().contains(employee.getDepartmentId())) {
            throw new AccessDeniedException("You are not allowed to access this employee");
        }
        if (scope.type() == EmployeeReadScopeType.SELF) {
            Employee requester = accessScopeService.findEmployee(requesterId).orElse(null);
            if (requester == null || !requester.getId().equals(employee.getId())) {
                throw new AccessDeniedException("You are not allowed to access this employee");
            }
        }
        return employeeMapper.toDto(employee);
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
            .supervisorEmployeeId(employee.getSupervisorEmployeeId())
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
        if (dto.supervisorEmployeeId() != null) {
            validateSupervisor(employee.getId(), dto.supervisorEmployeeId());
            employee.setSupervisorEmployeeId(dto.supervisorEmployeeId());
        }
        if (dto.workScheduleId() != null) {
            employee.setWorkScheduleId(dto.workScheduleId());
        }
        if (dto.location() != null) {
            employee.setLocation(dto.location().isBlank() ? null : dto.location().trim());
        }
        if (dto.cin() != null) {
            employee.setCin(dto.cin().isBlank() ? null : dto.cin().trim());
        }

        Employee saved = employeeRepository.save(employee);
        if (previous.getDepartmentId() != null && !previous.getDepartmentId().equals(saved.getDepartmentId())) {
            employeeHistoryService.recordDepartmentTransfer(previous, saved, actorId, LocalDate.now());
            analyticsEventPublisher.publishEmployeeTransferEvent(previous, saved);
        }
        if (previous.getStatus() != saved.getStatus()) {
            LocalDate effectiveDate = saved.getStatus() == EmployeeStatus.TERMINATED
                ? (saved.getTerminationDate() != null ? saved.getTerminationDate() : LocalDate.now())
                : LocalDate.now();
            String reason = saved.getStatus() == EmployeeStatus.TERMINATED ? "TERMINATION" : "STATUS_UPDATE";
            employeeHistoryService.recordStatusChange(previous, saved, actorId, effectiveDate, reason);
        }
        if (previous.getStatus() != EmployeeStatus.TERMINATED && saved.getStatus() == EmployeeStatus.TERMINATED) {
            if (saved.getTerminationDate() == null) {
                saved.setTerminationDate(LocalDate.now());
                saved = employeeRepository.save(saved);
            }
            analyticsEventPublisher.publishEmployeeTerminationEvent(saved);
        }

        auditLogService.log(actorId, AuditAction.UPDATE, "employee",
            saved.getId(), previous, saved);

        return employeeMapper.toDto(saved);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (employee.getStatus() != EmployeeStatus.INACTIVE && employee.getStatus() != EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Only deactivated or terminated employees can be deleted");
        }
        if (departmentRepository.existsByHeadEmployeeId(employee.getId())) {
            throw new IllegalStateException("Employee cannot be deleted because they are assigned as a department head");
        }
        if (employeeRepository.existsBySupervisorEmployeeId(employee.getId())) {
            throw new IllegalStateException("Employee cannot be deleted because they supervise other employees");
        }
        if (projectAssignmentRepository.existsByEmployeeId(employee.getId())
            || projectAssignmentRepository.existsBySupervisorId(employee.getId())) {
            throw new IllegalStateException("Employee cannot be deleted because they are referenced by project assignments");
        }
        if (leaveRequestRepository.existsByEmployeeId(employee.getId())) {
            throw new IllegalStateException("Employee cannot be deleted because they have leave requests");
        }

        leaveBalanceRepository.deleteByEmployeeId(employee.getId());
        employeeStatusHistoryRepository.deleteByEmployeeId(employee.getId());
        employeeDepartmentHistoryRepository.deleteByEmployeeId(employee.getId());
        employeeRepository.delete(employee);
        employeeRepository.flush();
        userDeletionService.deleteUser(employee.getUserId(), actorId);
        auditLogService.log(actorId, AuditAction.DELETE, "employee",
            employee.getId(), employee, null);
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
                    .totalDays(BigDecimal.valueOf(policy.get().getMaxDaysPerYear()))
                    .usedDays(BigDecimal.ZERO)
                    .pendingDays(BigDecimal.ZERO)
                    .carryOverDays(BigDecimal.ZERO)
                    .build();
                leaveBalanceRepository.save(balance);
            }
        }

        auditLogService.log(employee.getUserId(), AuditAction.CREATE,
            "leave_balance_initialization", employeeId, null, null);
    }

    private void validateSupervisor(UUID employeeId, UUID supervisorEmployeeId) {
        if (employeeId.equals(supervisorEmployeeId)) {
            throw new IllegalArgumentException("Employee cannot supervise themselves");
        }
        if (employeeRepository.findById(supervisorEmployeeId).isEmpty()) {
            throw new EntityNotFoundException("Supervisor employee not found");
        }
    }

    private EmployeeReadScope resolveReadScope(UUID requesterId) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(requesterId);
        if (scope.isGlobal()) {
            return EmployeeReadScope.global();
        }
        if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            if (scope.departmentIds().size() == 1) {
                return EmployeeReadScope.department(scope.departmentIds().get(0));
            }
            return EmployeeReadScope.departments(scope.departmentIds());
        }
        return EmployeeReadScope.self();
    }

    private record EmployeeReadScope(EmployeeReadScopeType type, UUID departmentId, List<UUID> departmentIds) {
        static EmployeeReadScope global() {
            return new EmployeeReadScope(EmployeeReadScopeType.GLOBAL, null, List.of());
        }

        static EmployeeReadScope department(UUID departmentId) {
            return new EmployeeReadScope(EmployeeReadScopeType.DEPARTMENT, departmentId, List.of(departmentId));
        }

        static EmployeeReadScope departments(List<UUID> departmentIds) {
            return new EmployeeReadScope(EmployeeReadScopeType.DEPARTMENTS, null, List.copyOf(departmentIds));
        }

        static EmployeeReadScope self() {
            return new EmployeeReadScope(EmployeeReadScopeType.SELF, null, List.of());
        }
    }

    private enum EmployeeReadScopeType {
        GLOBAL,
        DEPARTMENT,
        DEPARTMENTS,
        SELF
    }
}



