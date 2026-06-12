package com.hris.lifecycle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.service.EmployeeHistoryService;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.LocalAccountService;
import com.hris.lifecycle.dto.LifecycleDtos.ContractDto;
import com.hris.lifecycle.dto.LifecycleDtos.LifecycleEventDto;
import com.hris.lifecycle.dto.LifecycleDtos.LifecycleStateDto;
import com.hris.lifecycle.dto.LifecycleDtos.ReactivateRequest;
import com.hris.lifecycle.dto.LifecycleDtos.TerminateRequest;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Formal employee status transitions (see EMPLOYEE_LIFECYCLE_DESIGN.md §5).
 *
 * <p>Termination owns its side effects — contract closure, account deactivation,
 * session revocation, history, analytics — which is why the generic employee
 * update path rejects TERMINATED transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeLifecycleService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeContractRepository contractRepository;
    private final EmployeeStatusHistoryRepository statusHistoryRepository;
    private final EmployeeDepartmentHistoryRepository departmentHistoryRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final EmployeeHistoryService employeeHistoryService;
    private final LocalAccountService localAccountService;
    private final AuditLogService auditLogService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Terminates now when the date is today or earlier, otherwise schedules:
     * only termination_date is stamped and the daily lifecycle job executes the
     * transition when the date arrives.
     */
    @Transactional
    public LifecycleStateDto terminate(UUID employeeId, TerminateRequest request, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Employee is already terminated");
        }

        if (request.terminationDate().isAfter(LocalDate.now())) {
            employee.setTerminationDate(request.terminationDate());
            Employee saved = employeeRepository.save(employee);
            auditLogService.log(actorId, AuditAction.UPDATE, "employee_termination_scheduled",
                saved.getId(), null, saved);
            return getLifecycleStateInternal(saved);
        }

        Employee saved = executeTermination(employee, request.terminationDate(), request.reason(), actorId);
        return getLifecycleStateInternal(saved);
    }

    /** Shared by the immediate path and the daily job once a scheduled date is due. */
    @Transactional
    public Employee executeTermination(Employee employee, LocalDate terminationDate, String reason, UUID actorId) {
        Employee previous = snapshot(employee);

        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setTerminationDate(terminationDate);
        Employee saved = employeeRepository.save(employee);

        contractRepository.findByEmployeeIdAndStatus(saved.getId(), ContractStatus.ACTIVE)
            .ifPresent(contract -> {
                contract.setStatus(ContractStatus.TERMINATED);
                LocalDate end = terminationDate.isBefore(contract.getStartDate())
                    ? contract.getStartDate()
                    : terminationDate;
                contract.setEndDate(end);
                contractRepository.save(contract);
            });

        userRepository.findById(saved.getUserId()).ifPresent(user -> {
            user.setActive(false);
            userRepository.save(user);
            localAccountService.revokeAllSessions(user);
        });

        employeeHistoryService.recordStatusChange(previous, saved, actorId, terminationDate, reason);
        analyticsEventPublisher.publishEmployeeTerminationEvent(saved);
        auditLogService.log(actorId, AuditAction.UPDATE, "employee_termination",
            saved.getId(), previous, saved);
        notifySupervisorOfTermination(saved, terminationDate);
        return saved;
    }

    @Transactional
    public LifecycleStateDto cancelScheduledTermination(UUID employeeId, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Employee is already terminated — use reactivate instead");
        }
        if (employee.getTerminationDate() == null || !employee.getTerminationDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("No future-dated termination is scheduled for this employee");
        }
        Employee previous = snapshot(employee);
        employee.setTerminationDate(null);
        Employee saved = employeeRepository.save(employee);
        auditLogService.log(actorId, AuditAction.CANCEL, "employee_termination_scheduled",
            saved.getId(), previous, saved);
        return getLifecycleStateInternal(saved);
    }

    /**
     * TERMINATED/INACTIVE → ACTIVE. Re-enables the account (credentials are
     * recovered through the normal forgot-password flow). Contracts are not
     * resurrected — HR records a new contract for the new employment period.
     */
    @Transactional
    public LifecycleStateDto reactivate(UUID employeeId, ReactivateRequest request, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (employee.getStatus() != EmployeeStatus.TERMINATED && employee.getStatus() != EmployeeStatus.INACTIVE) {
            throw new IllegalStateException("Only terminated or inactive employees can be reactivated");
        }
        Employee previous = snapshot(employee);

        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setTerminationDate(null);
        Employee saved = employeeRepository.save(employee);

        userRepository.findById(saved.getUserId()).ifPresent(user -> {
            if (!user.isActive()) {
                user.setActive(true);
                userRepository.save(user);
            }
        });

        employeeHistoryService.recordStatusChange(previous, saved, actorId, LocalDate.now(), request.reason());
        auditLogService.log(actorId, AuditAction.UPDATE, "employee_reactivation",
            saved.getId(), previous, saved);
        return getLifecycleStateInternal(saved);
    }

    /** Read endpoint — scope enforced via EmployeeService.getById. */
    @Transactional(readOnly = true)
    public LifecycleStateDto getLifecycleState(UUID employeeId, UUID requesterId) {
        employeeService.getById(employeeId, requesterId);
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        return getLifecycleStateInternal(employee);
    }

    private LifecycleStateDto getLifecycleStateInternal(Employee employee) {
        ContractDto activeContract = contractRepository
            .findByEmployeeIdAndStatus(employee.getId(), ContractStatus.ACTIVE)
            .map(EmployeeContractService::toDto)
            .orElse(null);

        boolean scheduled = employee.getStatus() != EmployeeStatus.TERMINATED
            && employee.getTerminationDate() != null
            && employee.getTerminationDate().isAfter(LocalDate.now());

        return new LifecycleStateDto(
            employee.getStatus(),
            employee.getTerminationDate(),
            scheduled,
            activeContract,
            buildTimeline(employee.getId())
        );
    }

    private List<LifecycleEventDto> buildTimeline(UUID employeeId) {
        List<LifecycleEventDto> events = new ArrayList<>();

        statusHistoryRepository.findByEmployeeIdOrderByRecordedAtDesc(employeeId).forEach(h ->
            events.add(new LifecycleEventDto("STATUS", h.getEffectiveDate(), h.getRecordedAt(),
                h.getPreviousStatus(), h.getNewStatus(), null, null, null, null, null, h.getReason())));

        var transfers = departmentHistoryRepository.findByEmployeeIdOrderByRecordedAtDesc(employeeId);
        Set<UUID> departmentIds = new HashSet<>();
        transfers.forEach(t -> {
            if (t.getPreviousDepartmentId() != null) departmentIds.add(t.getPreviousDepartmentId());
            departmentIds.add(t.getNewDepartmentId());
        });
        Map<UUID, String> departmentNames = departmentRepository.findAllById(departmentIds).stream()
            .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
        transfers.forEach(t ->
            events.add(new LifecycleEventDto("TRANSFER", t.getEffectiveDate(), t.getRecordedAt(),
                null, null,
                t.getPreviousDepartmentId() != null ? departmentNames.get(t.getPreviousDepartmentId()) : null,
                departmentNames.get(t.getNewDepartmentId()),
                null, null, null, null)));

        contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId).forEach(c ->
            events.add(new LifecycleEventDto("CONTRACT", c.getStartDate(), c.getCreatedAt(),
                null, null, null, null,
                c.getContractType(), c.getStatus(), c.getEndDate(), c.getNote())));

        events.sort(Comparator.comparing(LifecycleEventDto::recordedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    private void notifySupervisorOfTermination(Employee employee, LocalDate terminationDate) {
        if (employee.getSupervisorEmployeeId() == null) {
            return;
        }
        employeeRepository.findById(employee.getSupervisorEmployeeId())
            .flatMap(supervisor -> userRepository.findById(supervisor.getUserId()))
            .ifPresent(supervisorUser -> publishLifecycleNotification(
                NotificationEventType.EMPLOYEE_TERMINATED,
                supervisorUser,
                employee,
                "lifecycle.terminated.title", "lifecycle.terminated.body",
                terminationDate.toString(), null));
    }

    /** Shared notification builder used here and by the daily lifecycle job. */
    void publishLifecycleNotification(NotificationEventType eventType, User target, Employee employee,
                                      String titleKey, String bodyKey, String date, String contractType) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("employeeName", employeeDisplayName(employee));
            params.put("date", date != null ? date : "");
            params.put("contractType", contractType != null ? contractType : "");
            params.put("linkPath", "/employees/" + employee.getId());

            notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                .eventType(eventType)
                .targetUserId(target.getId())
                .titleKey(titleKey)
                .bodyKey(bodyKey)
                .params(objectMapper.writeValueAsString(params))
                .locale(target.getLocalePreference())
                .routingKey("lifecycle." + eventType.name().toLowerCase())
                .publishedAt(Instant.now())
                .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize lifecycle notification params for employee {}", employee.getId(), e);
        }
    }

    String employeeDisplayName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(u -> (nullToEmpty(u.getFirstName()) + " " + nullToEmpty(u.getLastName())).trim())
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Employee snapshot(Employee employee) {
        return Employee.builder()
            .id(employee.getId())
            .userId(employee.getUserId())
            .employeeCode(employee.getEmployeeCode())
            .hireDate(employee.getHireDate())
            .jobTitle(employee.getJobTitle())
            .status(employee.getStatus())
            .contractType(employee.getContractType())
            .departmentId(employee.getDepartmentId())
            .supervisorEmployeeId(employee.getSupervisorEmployeeId())
            .terminationDate(employee.getTerminationDate())
            .workScheduleId(employee.getWorkScheduleId())
            .build();
    }
}
