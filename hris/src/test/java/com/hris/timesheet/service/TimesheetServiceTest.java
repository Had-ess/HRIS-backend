package com.hris.timesheet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.access.service.AccessResolutionService;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.service.WorkScheduleService;
import com.hris.security.service.AccessScopeService;
import com.hris.timesheet.dto.TimesheetDtos.EntryPayload;
import com.hris.timesheet.dto.TimesheetDtos.SummaryDto;
import com.hris.timesheet.dto.TimesheetDtos.TimesheetDto;
import com.hris.timesheet.entity.Timesheet;
import com.hris.timesheet.entity.TimesheetEntry;
import com.hris.timesheet.enums.TimesheetCategory;
import com.hris.timesheet.enums.TimesheetStatus;
import com.hris.timesheet.repository.TimesheetEntryRepository;
import com.hris.timesheet.repository.TimesheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimesheetService Unit Tests")
class TimesheetServiceTest {

    @Mock private TimesheetRepository timesheetRepository;
    @Mock private TimesheetEntryRepository timesheetEntryRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private WorkScheduleService workScheduleService;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;

    private TimesheetService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    /** A known Monday. */
    private final LocalDate monday = LocalDate.of(2026, 6, 8);

    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new TimesheetService(
            timesheetRepository, timesheetEntryRepository, employeeRepository, userRepository,
            projectRepository, accessScopeService, workScheduleService, leaveRequestRepository,
            notificationPublisher, auditLogService, new ObjectMapper());

        employee = Employee.builder()
            .id(employeeId)
            .userId(userId)
            .employeeCode("EMP-1")
            .workScheduleId(scheduleId)
            .build();

        lenient().when(accessScopeService.getEmployeeOrThrow(userId)).thenReturn(employee);
        lenient().when(accessScopeService.findEmployee(userId)).thenReturn(Optional.of(employee));
        lenient().when(timesheetRepository.save(any(Timesheet.class)))
            .thenAnswer(invocation -> {
                Timesheet sheet = invocation.getArgument(0);
                if (sheet.getId() == null) {
                    sheet.setId(UUID.randomUUID());
                }
                return sheet;
            });
        lenient().when(timesheetEntryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(any()))
            .thenReturn(List.of());
        lenient().when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(
            User.builder().id(userId).firstName("Test").lastName("User").localePreference("fr").build()));
    }

    private Timesheet sheet(TimesheetStatus status) {
        return Timesheet.builder()
            .id(UUID.randomUUID())
            .employeeId(employeeId)
            .periodStart(monday)
            .periodEnd(monday.plusDays(6))
            .status(status)
            .build();
    }

    @Test
    @DisplayName("create normalizes any day to the week's Monday")
    void createNormalizesToMonday() {
        when(timesheetRepository.findByEmployeeIdAndPeriodStart(employeeId, monday))
            .thenReturn(Optional.empty());

        TimesheetDto dto = service.create(userId, monday.plusDays(3)); // a Thursday

        assertThat(dto.periodStart()).isEqualTo(monday);
        assertThat(dto.periodEnd()).isEqualTo(monday.plusDays(6));
        assertThat(dto.status()).isEqualTo(TimesheetStatus.DRAFT);
    }

    @Test
    @DisplayName("create refuses a second sheet for the same week")
    void createRefusesDuplicateWeek() {
        when(timesheetRepository.findByEmployeeIdAndPeriodStart(employeeId, monday))
            .thenReturn(Optional.of(sheet(TimesheetStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(userId, monday))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("replaceEntries rejects dates outside the week")
    void replaceEntriesRejectsOutsideDates() {
        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        List<EntryPayload> payloads = List.of(
            new EntryPayload(monday.plusDays(9), null, TimesheetCategory.ADMIN, 60, null));

        assertThatThrownBy(() -> service.replaceEntries(userId, draft.getId(), payloads))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside");
    }

    @Test
    @DisplayName("replaceEntries rejects more than 24h declared on one day")
    void replaceEntriesRejectsOverfullDay() {
        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        List<EntryPayload> payloads = List.of(
            new EntryPayload(monday, null, TimesheetCategory.PROJECT, 1000, null),
            new EntryPayload(monday, null, TimesheetCategory.MEETING, 500, null));

        assertThatThrownBy(() -> service.replaceEntries(userId, draft.getId(), payloads))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("24");
    }

    @Test
    @DisplayName("replaceEntries refuses non-editable sheets")
    void replaceEntriesRefusesSubmittedSheet() {
        Timesheet submitted = sheet(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.replaceEntries(userId, submitted.getId(), List.of()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("submit refuses an empty timesheet")
    void submitRefusesEmptySheet() {
        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.submit(userId, draft.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("submit transitions to SUBMITTED and notifies the supervisor")
    void submitNotifiesSupervisor() {
        UUID supervisorEmployeeId = UUID.randomUUID();
        UUID supervisorUserId = UUID.randomUUID();
        employee.setSupervisorEmployeeId(supervisorEmployeeId);
        Employee supervisor = Employee.builder()
            .id(supervisorEmployeeId).userId(supervisorUserId).employeeCode("EMP-SUP").build();
        when(employeeRepository.findById(supervisorEmployeeId)).thenReturn(Optional.of(supervisor));
        when(userRepository.findById(supervisorUserId)).thenReturn(Optional.of(
            User.builder().id(supervisorUserId).firstName("Sup").lastName("Visor").localePreference("fr").build()));

        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(timesheetEntryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(draft.getId()))
            .thenReturn(List.of(TimesheetEntry.builder()
                .timesheetId(draft.getId()).workDate(monday)
                .category(TimesheetCategory.PROJECT).minutes(480).build()));

        TimesheetDto dto = service.submit(userId, draft.getId());

        assertThat(dto.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher).publishAfterCommit(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.TIMESHEET_SUBMITTED);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(supervisorUserId);
    }

    @Test
    @DisplayName("approve sets decision fields and notifies the employee")
    void approveSetsDecisionFields() {
        UUID approverUserId = UUID.randomUUID();
        Employee approver = Employee.builder()
            .id(UUID.randomUUID()).userId(approverUserId).employeeCode("EMP-APP").build();
        when(accessScopeService.findEmployee(approverUserId)).thenReturn(Optional.of(approver));
        when(accessScopeService.resolveDepartmentDataScope(approverUserId))
            .thenReturn(AccessResolutionService.ScopeResolution.global());

        Timesheet submitted = sheet(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        TimesheetDto dto = service.approve(approverUserId, submitted.getId());

        assertThat(dto.status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(submitted.getDecidedByUserId()).isEqualTo(approverUserId);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher).publishAfterCommit(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.TIMESHEET_APPROVED);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("approving your own timesheet is forbidden even with global scope")
    void selfApprovalForbidden() {
        when(accessScopeService.resolveDepartmentDataScope(userId))
            .thenReturn(AccessResolutionService.ScopeResolution.global());
        Timesheet submitted = sheet(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.approve(userId, submitted.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("own");
    }

    @Test
    @DisplayName("approver outside scope gets 404 semantics, not 403")
    void outOfScopeApproverGetsNotFound() {
        UUID strangerUserId = UUID.randomUUID();
        when(accessScopeService.findEmployee(strangerUserId)).thenReturn(Optional.empty());
        when(accessScopeService.resolveDepartmentDataScope(strangerUserId))
            .thenReturn(AccessResolutionService.ScopeResolution.self());

        Timesheet submitted = sheet(TimesheetStatus.SUBMITTED);
        when(timesheetRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.approve(strangerUserId, submitted.getId()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("reject requires a reason")
    void rejectRequiresReason() {
        assertThatThrownBy(() -> service.reject(userId, UUID.randomUUID(), "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("summary computes expected minutes from schedule minus approved leave")
    void summaryComputesExpectedMinutes() {
        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        draft.setTotalMinutes(2000);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        // Mon-Fri schedule, 8h/day; Wednesday is on approved leave
        when(workScheduleService.isWorkingDay(any(LocalDate.class), org.mockito.ArgumentMatchers.eq(scheduleId)))
            .thenAnswer(invocation -> {
                LocalDate day = invocation.getArgument(0);
                return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
            });
        when(workScheduleService.getHoursPerDay(scheduleId)).thenReturn(8);
        when(leaveRequestRepository
            .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(), any(), any(), any()))
            .thenReturn(List.of(LeaveRequest.builder()
                .id(UUID.randomUUID())
                .employeeId(employeeId)
                .startDate(monday.plusDays(2))
                .endDate(monday.plusDays(2))
                .build()));

        SummaryDto summary = service.summary(userId, draft.getId());

        assertThat(summary.expectedWorkingDays()).isEqualTo(4);
        assertThat(summary.leaveDays()).isEqualTo(1);
        assertThat(summary.expectedMinutes()).isEqualTo(4 * 8 * 60);
        assertThat(summary.declaredMinutes()).isEqualTo(2000);
        assertThat(summary.deltaMinutes()).isEqualTo(2000 - 1920);
    }

    @Test
    @DisplayName("summary returns null expectations when the employee has no schedule")
    void summaryWithoutScheduleReturnsNulls() {
        employee.setWorkScheduleId(null);
        Timesheet draft = sheet(TimesheetStatus.DRAFT);
        draft.setTotalMinutes(300);
        when(timesheetRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        SummaryDto summary = service.summary(userId, draft.getId());

        assertThat(summary.expectedMinutes()).isNull();
        assertThat(summary.deltaMinutes()).isNull();
        assertThat(summary.declaredMinutes()).isEqualTo(300);
    }
}
