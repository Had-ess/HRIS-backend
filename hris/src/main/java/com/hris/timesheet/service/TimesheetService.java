package com.hris.timesheet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.access.service.AccessResolutionService;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.entity.Project;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.service.WorkScheduleService;
import com.hris.security.service.AccessScopeService;
import com.hris.timesheet.dto.TimesheetDtos.EntryDto;
import com.hris.timesheet.dto.TimesheetDtos.EntryPayload;
import com.hris.timesheet.dto.TimesheetDtos.SummaryDto;
import com.hris.timesheet.dto.TimesheetDtos.TimesheetDto;
import com.hris.timesheet.entity.Timesheet;
import com.hris.timesheet.entity.TimesheetEntry;
import com.hris.timesheet.enums.TimesheetStatus;
import com.hris.timesheet.repository.TimesheetEntryRepository;
import com.hris.timesheet.repository.TimesheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Declarative weekly timesheets (TIME_ATTENDANCE_DESIGN.md). One sheet per
 * employee per ISO week; entries editable in DRAFT/REJECTED; single-step
 * approval by the direct supervisor or a department/global scope holder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetService {

    private static final int MAX_MINUTES_PER_DAY = 24 * 60;

    private final TimesheetRepository timesheetRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AccessScopeService accessScopeService;
    private final WorkScheduleService workScheduleService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Own-sheet operations (TIMESHEET_MANAGE_OWN)
    // ------------------------------------------------------------------

    @Transactional
    public TimesheetDto create(UUID userId, LocalDate requestedStart) {
        Employee employee = accessScopeService.getEmployeeOrThrow(userId);
        LocalDate periodStart = requestedStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        timesheetRepository.findByEmployeeIdAndPeriodStart(employee.getId(), periodStart)
            .ifPresent(existing -> {
                throw new IllegalStateException("A timesheet already exists for this week");
            });

        Timesheet saved = timesheetRepository.save(Timesheet.builder()
            .employeeId(employee.getId())
            .periodStart(periodStart)
            .periodEnd(periodStart.plusDays(6))
            .status(TimesheetStatus.DRAFT)
            .build());

        auditLogService.log(userId, AuditAction.CREATE, "timesheet", saved.getId(), null, stateOf(saved));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TimesheetDto> myTimesheets(UUID userId, LocalDate from, LocalDate to) {
        Employee employee = accessScopeService.getEmployeeOrThrow(userId);
        LocalDate effectiveTo = to != null ? to : LocalDate.now().plusWeeks(1);
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusWeeks(12);
        return timesheetRepository
            .findByEmployeeIdAndPeriodStartBetweenOrderByPeriodStartDesc(employee.getId(), effectiveFrom, effectiveTo)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public TimesheetDto replaceEntries(UUID userId, UUID timesheetId, List<EntryPayload> payloads) {
        Timesheet sheet = ownedSheet(userId, timesheetId);
        if (!sheet.isEditable()) {
            throw new IllegalStateException("Only draft or rejected timesheets can be edited");
        }
        validateEntries(sheet, payloads);

        timesheetEntryRepository.deleteByTimesheetId(sheet.getId());
        List<TimesheetEntry> entries = payloads.stream()
            .map(payload -> TimesheetEntry.builder()
                .timesheetId(sheet.getId())
                .workDate(payload.workDate())
                .projectId(payload.projectId())
                .category(payload.category())
                .minutes(payload.minutes())
                .note(payload.note() != null && !payload.note().isBlank() ? payload.note().trim() : null)
                .build())
            .toList();
        timesheetEntryRepository.saveAll(entries);

        sheet.setTotalMinutes(payloads.stream().mapToInt(EntryPayload::minutes).sum());
        return toDto(timesheetRepository.save(sheet));
    }

    @Transactional
    public TimesheetDto submit(UUID userId, UUID timesheetId) {
        Timesheet sheet = ownedSheet(userId, timesheetId);
        if (!sheet.isEditable()) {
            throw new IllegalStateException("Only draft or rejected timesheets can be submitted");
        }
        if (timesheetEntryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(sheet.getId()).isEmpty()) {
            throw new IllegalStateException("Cannot submit an empty timesheet");
        }

        Map<String, Object> previous = stateOf(sheet);
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        sheet.setSubmittedAt(Instant.now());
        sheet.setRejectionReason(null);
        sheet.setDecidedAt(null);
        sheet.setDecidedByUserId(null);
        Timesheet saved = timesheetRepository.save(sheet);

        auditLogService.log(userId, AuditAction.SUBMIT, "timesheet", saved.getId(), previous, stateOf(saved));
        notifyApprovers(saved, userId);
        return toDto(saved);
    }

    // ------------------------------------------------------------------
    // Approver operations (TIMESHEET_READ / TIMESHEET_APPROVE)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TimesheetDto> pendingForApprover(UUID userId) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(userId);
        UUID approverEmployeeId = accessScopeService.findEmployee(userId).map(Employee::getId).orElse(null);

        List<Timesheet> pending;
        if (scope.isGlobal()) {
            pending = timesheetRepository.findByStatusOrderBySubmittedAtAsc(TimesheetStatus.SUBMITTED);
        } else if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            pending = timesheetRepository.findPendingForDepartmentsOrSupervisor(
                TimesheetStatus.SUBMITTED, scope.departmentIds(), approverEmployeeId);
        } else if (approverEmployeeId != null) {
            pending = timesheetRepository.findPendingForSupervisor(TimesheetStatus.SUBMITTED, approverEmployeeId);
        } else {
            pending = List.of();
        }

        return pending.stream()
            .filter(sheet -> !sheet.getEmployeeId().equals(approverEmployeeId)) // never your own
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public TimesheetDto approve(UUID userId, UUID timesheetId) {
        return decide(userId, timesheetId, TimesheetStatus.APPROVED, null);
    }

    @Transactional
    public TimesheetDto reject(UUID userId, UUID timesheetId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        return decide(userId, timesheetId, TimesheetStatus.REJECTED, reason.trim());
    }

    // ------------------------------------------------------------------
    // Shared reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TimesheetDto get(UUID userId, UUID timesheetId) {
        return toDto(readableSheet(userId, timesheetId));
    }

    /**
     * Expected-vs-declared. Expected = working days of the period (schedule +
     * holiday calendar) not covered by approved leave, times hours/day. Null
     * expected when the employee has no work schedule. Half-day leaves count
     * as full leave days (documented first-cut simplification).
     */
    @Transactional(readOnly = true)
    public SummaryDto summary(UUID userId, UUID timesheetId) {
        Timesheet sheet = readableSheet(userId, timesheetId);
        Employee employee = employeeRepository.findById(sheet.getEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (employee.getWorkScheduleId() == null) {
            return new SummaryDto(sheet.getId(), null, null, null, sheet.getTotalMinutes(), null);
        }

        Set<LocalDate> leaveDates = approvedLeaveDates(employee.getId(), sheet.getPeriodStart(), sheet.getPeriodEnd());
        int expectedDays = 0;
        int leaveDays = 0;
        for (LocalDate day = sheet.getPeriodStart(); !day.isAfter(sheet.getPeriodEnd()); day = day.plusDays(1)) {
            if (!workScheduleService.isWorkingDay(day, employee.getWorkScheduleId())) {
                continue;
            }
            if (leaveDates.contains(day)) {
                leaveDays++;
            } else {
                expectedDays++;
            }
        }

        int expectedMinutes = expectedDays * workScheduleService.getHoursPerDay(employee.getWorkScheduleId()) * 60;
        return new SummaryDto(sheet.getId(), expectedDays, leaveDays, expectedMinutes,
            sheet.getTotalMinutes(), sheet.getTotalMinutes() - expectedMinutes);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private TimesheetDto decide(UUID userId, UUID timesheetId, TimesheetStatus decision, String reason) {
        Timesheet sheet = timesheetRepository.findById(timesheetId)
            .orElseThrow(() -> new EntityNotFoundException("Timesheet not found"));
        if (!inApproverScope(userId, sheet)) {
            // 404, not 403: approvers must not learn which sheet ids exist outside their scope
            throw new EntityNotFoundException("Timesheet not found");
        }
        Employee approver = accessScopeService.findEmployee(userId).orElse(null);
        if (approver != null && approver.getId().equals(sheet.getEmployeeId())) {
            throw new IllegalStateException("You cannot decide on your own timesheet");
        }
        if (sheet.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted timesheets can be decided");
        }

        Map<String, Object> previous = stateOf(sheet);
        sheet.setStatus(decision);
        sheet.setDecidedAt(Instant.now());
        sheet.setDecidedByUserId(userId);
        sheet.setRejectionReason(reason);
        Timesheet saved = timesheetRepository.save(sheet);

        auditLogService.log(userId,
            decision == TimesheetStatus.APPROVED ? AuditAction.APPROVE : AuditAction.REJECT,
            "timesheet", saved.getId(), previous, stateOf(saved));
        notifyEmployee(saved, decision, reason);
        return toDto(saved);
    }

    private Timesheet ownedSheet(UUID userId, UUID timesheetId) {
        Employee employee = accessScopeService.getEmployeeOrThrow(userId);
        Timesheet sheet = timesheetRepository.findById(timesheetId)
            .orElseThrow(() -> new EntityNotFoundException("Timesheet not found"));
        if (!sheet.getEmployeeId().equals(employee.getId())) {
            throw new EntityNotFoundException("Timesheet not found");
        }
        return sheet;
    }

    private Timesheet readableSheet(UUID userId, UUID timesheetId) {
        Timesheet sheet = timesheetRepository.findById(timesheetId)
            .orElseThrow(() -> new EntityNotFoundException("Timesheet not found"));
        boolean own = accessScopeService.findEmployee(userId)
            .map(employee -> employee.getId().equals(sheet.getEmployeeId()))
            .orElse(false);
        if (own) {
            return sheet;
        }
        if (accessScopeService.hasPermissionName(userId, "TIMESHEET_READ") && inApproverScope(userId, sheet)) {
            return sheet;
        }
        throw new EntityNotFoundException("Timesheet not found");
    }

    /** Global scope → everything; department scope → their departments; everyone → direct reports. */
    private boolean inApproverScope(UUID userId, Timesheet sheet) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(userId);
        if (scope.isGlobal()) {
            return true;
        }
        Employee target = employeeRepository.findById(sheet.getEmployeeId()).orElse(null);
        if (target == null) {
            return false;
        }
        UUID approverEmployeeId = accessScopeService.findEmployee(userId).map(Employee::getId).orElse(null);
        boolean supervises = approverEmployeeId != null
            && approverEmployeeId.equals(target.getSupervisorEmployeeId());
        if (scope.isDepartment()) {
            return supervises
                || (target.getDepartmentId() != null && scope.departmentIds().contains(target.getDepartmentId()));
        }
        return supervises;
    }

    private void validateEntries(Timesheet sheet, List<EntryPayload> payloads) {
        Map<LocalDate, Integer> minutesPerDay = new LinkedHashMap<>();
        Set<UUID> projectIds = new HashSet<>();
        for (EntryPayload payload : payloads) {
            if (payload.workDate().isBefore(sheet.getPeriodStart())
                || payload.workDate().isAfter(sheet.getPeriodEnd())) {
                throw new IllegalArgumentException("Entry date " + payload.workDate() + " is outside the timesheet week");
            }
            minutesPerDay.merge(payload.workDate(), payload.minutes(), Integer::sum);
            if (payload.projectId() != null) {
                projectIds.add(payload.projectId());
            }
        }
        minutesPerDay.forEach((day, total) -> {
            if (total > MAX_MINUTES_PER_DAY) {
                throw new IllegalArgumentException("Declared time on " + day + " exceeds 24 hours");
            }
        });
        if (!projectIds.isEmpty()) {
            Set<UUID> known = projectRepository.findAllById(projectIds).stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
            projectIds.stream().filter(id -> !known.contains(id)).findFirst().ifPresent(id -> {
                throw new IllegalArgumentException("Unknown project: " + id);
            });
        }
    }

    private Set<LocalDate> approvedLeaveDates(UUID employeeId, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        leaveRequestRepository
            .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId, LeaveStatus.APPROVED, to, from)
            .forEach(leave -> {
                LocalDate day = leave.getStartDate().isBefore(from) ? from : leave.getStartDate();
                LocalDate end = leave.getEndDate().isAfter(to) ? to : leave.getEndDate();
                for (; !day.isAfter(end); day = day.plusDays(1)) {
                    dates.add(day);
                }
            });
        return dates;
    }

    private void notifyApprovers(Timesheet sheet, UUID submitterUserId) {
        Employee employee = employeeRepository.findById(sheet.getEmployeeId()).orElse(null);
        if (employee == null) {
            return;
        }
        List<User> recipients = supervisorUser(employee)
            .map(List::of)
            .orElseGet(() -> userRepository.findByPermissionNames(List.of("TIMESHEET_APPROVE")));

        String employeeName = displayName(employee);
        recipients.stream()
            .filter(user -> !user.getId().equals(submitterUserId))
            .forEach(user -> notificationPublisher.publishAfterCommit(event(
                NotificationEventType.TIMESHEET_SUBMITTED, user,
                "timesheet.submitted.title", "timesheet.submitted.body", "timesheet.submitted",
                employeeName, sheet, null, "/timesheet-approvals")));
    }

    private void notifyEmployee(Timesheet sheet, TimesheetStatus decision, String reason) {
        Employee employee = employeeRepository.findById(sheet.getEmployeeId()).orElse(null);
        if (employee == null || employee.getUserId() == null) {
            return;
        }
        User user = userRepository.findById(employee.getUserId()).orElse(null);
        if (user == null) {
            return;
        }
        boolean approved = decision == TimesheetStatus.APPROVED;
        notificationPublisher.publishAfterCommit(event(
            approved ? NotificationEventType.TIMESHEET_APPROVED : NotificationEventType.TIMESHEET_REJECTED,
            user,
            approved ? "timesheet.approved.title" : "timesheet.rejected.title",
            approved ? "timesheet.approved.body" : "timesheet.rejected.body",
            approved ? "timesheet.approved" : "timesheet.rejected",
            displayName(employee), sheet, reason, "/timesheets"));
    }

    private NotificationEvent event(NotificationEventType type, User target, String titleKey, String bodyKey,
                                    String routingKey, String employeeName, Timesheet sheet,
                                    String reason, String linkPath) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("employeeName", employeeName);
        params.put("periodStart", sheet.getPeriodStart().toString());
        params.put("periodEnd", sheet.getPeriodEnd().toString());
        params.put("rejectionReason", reason == null ? "" : reason);
        params.put("linkPath", linkPath);
        return NotificationEvent.builder()
            .eventType(type)
            .targetUserId(target.getId())
            .titleKey(titleKey)
            .bodyKey(bodyKey)
            .params(serialize(params))
            .locale(target.getLocalePreference() == null || target.getLocalePreference().isBlank()
                ? "fr" : target.getLocalePreference())
            .routingKey(routingKey)
            .publishedAt(Instant.now())
            .build();
    }

    private java.util.Optional<User> supervisorUser(Employee employee) {
        return java.util.Optional.ofNullable(employee.getSupervisorEmployeeId())
            .flatMap(employeeRepository::findById)
            .map(Employee::getUserId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById);
    }

    private String displayName(Employee employee) {
        return java.util.Optional.ofNullable(employee.getUserId())
            .flatMap(userRepository::findById)
            .map(user -> (user.getFirstName() + " " + user.getLastName()).trim())
            .orElse(employee.getEmployeeCode());
    }

    private TimesheetDto toDto(Timesheet sheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository
            .findByTimesheetIdOrderByWorkDateAscIdAsc(sheet.getId());
        Set<UUID> projectIds = entries.stream()
            .map(TimesheetEntry::getProjectId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> projectNames = projectIds.isEmpty() ? Map.of()
            : projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        String employeeName = employeeRepository.findById(sheet.getEmployeeId())
            .map(this::displayName)
            .orElse("");

        List<EntryDto> entryDtos = entries.stream()
            .map(entry -> new EntryDto(
                entry.getId(), entry.getWorkDate(), entry.getProjectId(),
                entry.getProjectId() != null ? projectNames.get(entry.getProjectId()) : null,
                entry.getCategory(), entry.getMinutes(), entry.getNote()))
            .toList();

        return new TimesheetDto(
            sheet.getId(), sheet.getEmployeeId(), employeeName,
            sheet.getPeriodStart(), sheet.getPeriodEnd(), sheet.getStatus(),
            sheet.getTotalMinutes(), sheet.getSubmittedAt(), sheet.getDecidedAt(),
            sheet.getRejectionReason(), entryDtos);
    }

    private Map<String, Object> stateOf(Timesheet sheet) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", sheet.getStatus().name());
        state.put("periodStart", sheet.getPeriodStart().toString());
        state.put("totalMinutes", sheet.getTotalMinutes());
        return state;
    }

    private String serialize(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification payload", ex);
        }
    }
}
