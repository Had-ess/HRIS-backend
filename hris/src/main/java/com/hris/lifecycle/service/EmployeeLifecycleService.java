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
import com.hris.lifecycle.dto.LifecycleDtos.ScheduledTransferDto;
import com.hris.lifecycle.dto.LifecycleDtos.TerminateRequest;
import com.hris.lifecycle.dto.LifecycleDtos.TransferRequest;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.TeamRepository;
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
    private final TeamRepository teamRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TeamHierarchyRelationRepository teamHierarchyRelationRepository;
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
        assertNoActiveResponsibilities(employee);

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
        // a pending transfer makes no sense for a terminated employee
        employee.setScheduledTransferDate(null);
        employee.setScheduledTransferDepartmentId(null);
        employee.setScheduledTransferSupervisorId(null);
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

        endProjectAssignments(saved.getId(), terminationDate, actorId);
        endTeamHierarchyMemberships(saved.getId(), terminationDate, actorId);

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

    /**
     * Transfers now when the effective date is today or earlier, otherwise
     * schedules: the scheduled_transfer_* columns are stamped and the daily
     * lifecycle job executes the move when the date arrives (mirrors the
     * scheduled-termination pattern).
     */
    @Transactional
    public LifecycleStateDto transfer(UUID employeeId, TransferRequest request, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Terminated employees cannot be transferred");
        }
        if (request.departmentId() == null && request.supervisorEmployeeId() == null) {
            throw new IllegalArgumentException(
                "A transfer must change the department and/or the supervisor");
        }
        if (request.departmentId() != null && request.departmentId().equals(employee.getDepartmentId())) {
            throw new IllegalArgumentException("Employee is already in the target department");
        }

        if (request.effectiveDate().isAfter(LocalDate.now())) {
            if (employee.getScheduledTransferDate() != null) {
                throw new IllegalStateException(
                    "A transfer is already scheduled for this employee — cancel it first");
            }
            // executeTransfer re-validates when the date arrives; validate here so
            // an invalid target is rejected at scheduling time, not silently later
            validateTransferTargets(employee, request.departmentId(), request.supervisorEmployeeId());
            Employee previous = snapshot(employee);
            employee.setScheduledTransferDate(request.effectiveDate());
            employee.setScheduledTransferDepartmentId(request.departmentId());
            employee.setScheduledTransferSupervisorId(request.supervisorEmployeeId());
            Employee saved = employeeRepository.save(employee);
            auditLogService.log(actorId, AuditAction.UPDATE, "employee_transfer_scheduled",
                saved.getId(), previous, saved);
            return getLifecycleStateInternal(saved);
        }

        Employee saved = executeTransfer(employee, request.effectiveDate(),
            request.departmentId(), request.supervisorEmployeeId(), actorId);
        return getLifecycleStateInternal(saved);
    }

    /**
     * Shared by the immediate path and the daily job once a scheduled date is
     * due. Targets are re-validated here — the department may have been
     * deactivated or the supervisor terminated since the transfer was scheduled.
     */
    @Transactional
    public Employee executeTransfer(Employee employee, LocalDate effectiveDate,
                                    UUID departmentId, UUID supervisorEmployeeId, UUID actorId) {
        validateTransferTargets(employee, departmentId, supervisorEmployeeId);
        Employee previous = snapshot(employee);

        if (departmentId != null) {
            employee.setDepartmentId(departmentId);
        }
        if (supervisorEmployeeId != null) {
            employee.setSupervisorEmployeeId(supervisorEmployeeId);
        }
        employee.setScheduledTransferDate(null);
        employee.setScheduledTransferDepartmentId(null);
        employee.setScheduledTransferSupervisorId(null);
        Employee saved = employeeRepository.save(employee);

        if (previous.getDepartmentId() != null
            && !previous.getDepartmentId().equals(saved.getDepartmentId())) {
            employeeHistoryService.recordDepartmentTransfer(previous, saved, actorId, effectiveDate);
            analyticsEventPublisher.publishEmployeeTransferEvent(previous, saved);
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "employee_transfer",
            saved.getId(), previous, saved);
        notifyTransfer(saved, effectiveDate);
        return saved;
    }

    @Transactional
    public LifecycleStateDto cancelScheduledTransfer(UUID employeeId, UUID actorId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (employee.getScheduledTransferDate() == null) {
            throw new IllegalStateException("No transfer is scheduled for this employee");
        }
        Employee previous = snapshot(employee);
        employee.setScheduledTransferDate(null);
        employee.setScheduledTransferDepartmentId(null);
        employee.setScheduledTransferSupervisorId(null);
        Employee saved = employeeRepository.save(employee);
        auditLogService.log(actorId, AuditAction.CANCEL, "employee_transfer_scheduled",
            saved.getId(), previous, saved);
        return getLifecycleStateInternal(saved);
    }

    private void validateTransferTargets(Employee employee, UUID departmentId, UUID supervisorEmployeeId) {
        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Target department not found"));
            if (!department.isActive()) {
                throw new IllegalArgumentException("Target department must be active");
            }
        }
        if (supervisorEmployeeId != null) {
            employeeService.validateSupervisorAssignment(employee.getId(), supervisorEmployeeId);
        }
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

        ScheduledTransferDto scheduledTransfer = null;
        if (employee.getScheduledTransferDate() != null) {
            String departmentName = employee.getScheduledTransferDepartmentId() != null
                ? departmentRepository.findById(employee.getScheduledTransferDepartmentId())
                    .map(Department::getName).orElse(null)
                : null;
            String supervisorName = employee.getScheduledTransferSupervisorId() != null
                ? employeeRepository.findById(employee.getScheduledTransferSupervisorId())
                    .map(this::employeeDisplayName).orElse(null)
                : null;
            scheduledTransfer = new ScheduledTransferDto(
                employee.getScheduledTransferDate(),
                employee.getScheduledTransferDepartmentId(),
                departmentName,
                employee.getScheduledTransferSupervisorId(),
                supervisorName
            );
        }

        return new LifecycleStateDto(
            employee.getStatus(),
            employee.getTerminationDate(),
            scheduled,
            scheduledTransfer,
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

    /**
     * Termination is refused while the employee still carries leadership
     * responsibilities — HR must reassign them first so approvals, teams and
     * departments never point at a terminated employee. Checked when the
     * termination is requested (immediate or scheduled) and re-checked by the
     * daily job before executing a due scheduled termination.
     */
    public void assertNoActiveResponsibilities(Employee employee) {
        List<String> blockers = new ArrayList<>();
        if (departmentRepository.existsByHeadEmployeeId(employee.getId())) {
            blockers.add("is a department head (assign a new head first)");
        }
        if (employeeRepository.existsBySupervisorEmployeeIdAndStatusNot(
                employee.getId(), EmployeeStatus.TERMINATED)) {
            blockers.add("supervises other employees (reassign their supervisor first)");
        }
        if (teamRepository.existsBySupervisorEmployeeIdAndIsActiveTrue(employee.getId())) {
            blockers.add("supervises one or more teams (assign a new team supervisor first)");
        }
        if (projectAssignmentRepository.countActiveDistinctEmployeesBySupervisorId(
                employee.getId(), LocalDate.now()) > 0) {
            blockers.add("supervises active project assignments (reassign them first)");
        }
        if (teamHierarchyRelationRepository.existsByResponsibleEmployeeIdAndStatus(
                employee.getId(), TeamHierarchyStatus.ACTIVE)) {
            blockers.add("is responsible for collaborators in a team hierarchy (reassign them first)");
        }
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(
                "Employee cannot be terminated: " + String.join("; ", blockers));
        }
    }

    /** Closes the employee's own active project assignments as of the termination date. */
    private void endProjectAssignments(UUID employeeId, LocalDate terminationDate, UUID actorId) {
        for (ProjectAssignment assignment : projectAssignmentRepository.findByEmployeeIdAndIsActiveTrue(employeeId)) {
            assignment.setActive(false);
            LocalDate end = terminationDate.isBefore(assignment.getStartDate())
                ? assignment.getStartDate()
                : terminationDate;
            if (assignment.getEndDate() == null || assignment.getEndDate().isAfter(end)) {
                assignment.setEndDate(end);
            }
            projectAssignmentRepository.save(assignment);
            auditLogService.log(actorId, AuditAction.UPDATE, "project_assignment_ended_by_termination",
                assignment.getId(), null, assignment);
        }
    }

    /** Ends the employee's own collaborator relations in team hierarchies. */
    private void endTeamHierarchyMemberships(UUID employeeId, LocalDate terminationDate, UUID actorId) {
        for (TeamHierarchyRelation relation : teamHierarchyRelationRepository
                .findByCollaboratorEmployeeIdAndStatusOrderByStartDateAscTeamIdAsc(
                    employeeId, TeamHierarchyStatus.ACTIVE)) {
            relation.setStatus(TeamHierarchyStatus.ENDED);
            LocalDate end = terminationDate.isBefore(relation.getStartDate())
                ? relation.getStartDate()
                : terminationDate;
            if (relation.getEndDate() == null || relation.getEndDate().isAfter(end)) {
                relation.setEndDate(end);
            }
            teamHierarchyRelationRepository.save(relation);
            auditLogService.log(actorId, AuditAction.UPDATE, "team_hierarchy_relation_ended_by_termination",
                relation.getId(), null, relation);
        }
    }

    /** Notifies the moved employee and (when set) the new supervisor. */
    private void notifyTransfer(Employee employee, LocalDate effectiveDate) {
        String departmentName = employee.getDepartmentId() != null
            ? departmentRepository.findById(employee.getDepartmentId())
                .map(Department::getName).orElse("")
            : "";
        userRepository.findById(employee.getUserId()).ifPresent(user ->
            publishTransferNotification(user, employee, effectiveDate, departmentName));
        if (employee.getSupervisorEmployeeId() != null) {
            employeeRepository.findById(employee.getSupervisorEmployeeId())
                .flatMap(supervisor -> userRepository.findById(supervisor.getUserId()))
                .ifPresent(supervisorUser ->
                    publishTransferNotification(supervisorUser, employee, effectiveDate, departmentName));
        }
    }

    private void publishTransferNotification(User target, Employee employee,
                                             LocalDate effectiveDate, String departmentName) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("employeeName", employeeDisplayName(employee));
            params.put("date", effectiveDate.toString());
            params.put("departmentName", departmentName != null ? departmentName : "");
            params.put("linkPath", "/employees/" + employee.getId());

            notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                .eventType(NotificationEventType.EMPLOYEE_TRANSFERRED)
                .targetUserId(target.getId())
                .titleKey("lifecycle.transferred.title")
                .bodyKey("lifecycle.transferred.body")
                .params(objectMapper.writeValueAsString(params))
                .locale(target.getLocalePreference())
                .routingKey("lifecycle.employee_transferred")
                .publishedAt(Instant.now())
                .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize transfer notification params for employee {}", employee.getId(), e);
        }
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
            .jobTitleId(employee.getJobTitleId())
            .status(employee.getStatus())
            .contractType(employee.getContractType())
            .departmentId(employee.getDepartmentId())
            .supervisorEmployeeId(employee.getSupervisorEmployeeId())
            .terminationDate(employee.getTerminationDate())
            .scheduledTransferDate(employee.getScheduledTransferDate())
            .scheduledTransferDepartmentId(employee.getScheduledTransferDepartmentId())
            .scheduledTransferSupervisorId(employee.getScheduledTransferSupervisorId())
            .workScheduleId(employee.getWorkScheduleId())
            .build();
    }
}
