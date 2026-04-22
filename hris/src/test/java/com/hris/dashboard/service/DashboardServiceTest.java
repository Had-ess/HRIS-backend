package com.hris.dashboard.service;

import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.entity.LeaveMetrics;
import com.hris.analytics.repository.LeaveMetricsRepository;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.analytics.dto.LeaveMetricsDto;
import com.hris.analytics.service.AnalyticsService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.dashboard.dto.DirectorDashboardDto;
import com.hris.dashboard.dto.EmployeeDashboardDto;
import com.hris.dashboard.dto.HrDashboardDto;
import com.hris.dashboard.dto.SupervisorDashboardDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.repository.NotificationRepository;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Unit Tests")
class DashboardServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private AdminRequestTypeRepository adminRequestTypeRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AnalyticsService analyticsService;

    @InjectMocks
    private DashboardService dashboardService;

    private UUID userId;
    private UUID employeeId;
    private UUID departmentId;
    private UUID leaveTypeId;
    private UUID requestTypeId;
    private Employee employee;
    private LeaveType leaveType;
    private AdminRequestType adminRequestType;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        leaveTypeId = UUID.randomUUID();
        requestTypeId = UUID.randomUUID();

        employee = Employee.builder()
            .id(employeeId)
            .userId(userId)
            .departmentId(departmentId)
            .build();

        leaveType = LeaveType.builder()
            .id(leaveTypeId)
            .name("Annual Leave")
            .build();

        adminRequestType = AdminRequestType.builder()
            .id(requestTypeId)
            .name("Certificate")
            .build();
    }

    @Test
    @DisplayName("employee dashboard returns expected sections")
    void employeeDashboardReturnsExpectedSections() {
        LeaveBalance leaveBalance = LeaveBalance.builder()
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .year(LocalDate.now().getYear())
            .totalDays(20)
            .usedDays(5)
            .pendingDays(2)
            .carryOverDays(1)
            .build();
        LeaveRequest leaveRequest = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .startDate(LocalDate.now().plusDays(5))
            .endDate(LocalDate.now().plusDays(7))
            .workingDays(3)
            .status(LeaveStatus.IN_APPROVAL)
            .submittedAt(Instant.now())
            .build();
        AdminRequest adminRequest = AdminRequest.builder()
            .id(UUID.randomUUID())
            .requesterId(userId)
            .requestTypeId(requestTypeId)
            .trackingNumber("AR-20260417-00001")
            .status(AdminRequestStatus.SUBMITTED)
            .urgencyLevel(UrgencyLevel.NORMAL)
            .submittedAt(Instant.now())
            .build();

        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(4L);
        when(leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, LocalDate.now().getYear()))
            .thenReturn(List.of(leaveBalance));
        when(leaveRequestRepository.findTop5ByEmployeeIdOrderBySubmittedAtDesc(employeeId))
            .thenReturn(List.of(leaveRequest));
        when(adminRequestRepository.findTop5ByRequesterIdOrderBySubmittedAtDesc(userId))
            .thenReturn(List.of(adminRequest));
        when(leaveTypeRepository.findAllById(any())).thenReturn(List.of(leaveType));
        when(adminRequestTypeRepository.findAllById(any()))
            .thenReturn(List.of(adminRequestType));

        EmployeeDashboardDto result = dashboardService.getEmployeeDashboard(userId);

        assertThat(result.unreadNotificationsCount()).isEqualTo(4L);
        assertThat(result.leaveBalances()).hasSize(1);
        assertThat(result.leaveBalances().get(0).leaveTypeName()).isEqualTo("Annual Leave");
        assertThat(result.recentLeaveRequests()).hasSize(1);
        assertThat(result.recentLeaveRequests().get(0).leaveTypeName()).isEqualTo("Annual Leave");
        assertThat(result.recentAdminRequests()).hasSize(1);
        assertThat(result.recentAdminRequests().get(0).requestTypeName()).isEqualTo("Certificate");
    }

    @Test
    @DisplayName("supervisor dashboard returns pending approvals count")
    void supervisorDashboardReturnsPendingApprovalsCount() {
        ApprovalStep approvalStep = ApprovalStep.builder()
            .id(UUID.randomUUID())
            .workflowId(UUID.randomUUID())
            .context(ApprovalContext.DEPARTMENT)
            .stepOrder(1)
            .status(StepStatus.PENDING)
            .build();
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
            .id(approvalStep.getWorkflowId())
            .subjectType("LEAVE")
            .subjectId(UUID.randomUUID())
            .createdAt(Instant.now())
            .build();

        when(approvalStepRepository.countByApproverIdAndStatus(userId, StepStatus.PENDING)).thenReturn(3L);
        when(approvalStepRepository.findTop5ByApproverIdAndStatusOrderByStepOrderAsc(userId, StepStatus.PENDING))
            .thenReturn(List.of(approvalStep));
        when(approvalWorkflowRepository.findAllById(any()))
            .thenReturn(List.of(workflow));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(userRoleRepository.findEffectiveByUserId(any(), any())).thenReturn(List.of(
            UserRole.builder()
                .role(Role.builder().code("DEPT_MANAGER").build())
                .build()
        ));
        when(employeeRepository.countByDepartmentId(departmentId)).thenReturn(7L);
        when(leaveRequestRepository.countUpcomingDepartmentRequests(
            departmentId, LeaveStatus.APPROVED, LocalDate.now())).thenReturn(2L);

        SupervisorDashboardDto result = dashboardService.getSupervisorDashboard(userId);

        assertThat(result.pendingApprovalsCount()).isEqualTo(3L);
        assertThat(result.recentPendingApprovals()).hasSize(1);
        assertThat(result.recentPendingApprovals().get(0).subjectType()).isEqualTo("LEAVE");
        assertThat(result.teamMembersCount()).isEqualTo(7L);
        assertThat(result.upcomingTeamAbsencesCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("project supervisor dashboard uses supervised project scope")
    void projectSupervisorDashboardUsesSupervisedProjectScope() {
        ApprovalStep approvalStep = ApprovalStep.builder()
            .id(UUID.randomUUID())
            .workflowId(UUID.randomUUID())
            .context(ApprovalContext.PROJECT)
            .stepOrder(1)
            .status(StepStatus.PENDING)
            .build();
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
            .id(approvalStep.getWorkflowId())
            .subjectType("LEAVE")
            .subjectId(UUID.randomUUID())
            .createdAt(Instant.now())
            .build();

        when(approvalStepRepository.countByApproverIdAndStatus(userId, StepStatus.PENDING)).thenReturn(2L);
        when(approvalStepRepository.findTop5ByApproverIdAndStatusOrderByStepOrderAsc(userId, StepStatus.PENDING))
            .thenReturn(List.of(approvalStep));
        when(approvalWorkflowRepository.findAllById(any()))
            .thenReturn(List.of(workflow));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(userRoleRepository.findEffectiveByUserId(any(), any())).thenReturn(List.of(
            UserRole.builder()
                .role(Role.builder().code("PROJECT_SUPERVISOR").build())
                .build()
        ));
        when(projectAssignmentRepository.countActiveDistinctEmployeesBySupervisorId(employeeId, LocalDate.now()))
            .thenReturn(5L);
        when(leaveRequestRepository.countUpcomingSupervisorRequests(
            employeeId, LeaveStatus.APPROVED, LocalDate.now())).thenReturn(3L);

        SupervisorDashboardDto result = dashboardService.getSupervisorDashboard(userId);

        assertThat(result.pendingApprovalsCount()).isEqualTo(2L);
        assertThat(result.recentPendingApprovals()).hasSize(1);
        assertThat(result.teamMembersCount()).isEqualTo(5L);
        assertThat(result.upcomingTeamAbsencesCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("HR dashboard returns key counts")
    void hrDashboardReturnsKeyCounts() {
        AdminRequest adminRequest = AdminRequest.builder()
            .id(UUID.randomUUID())
            .requestTypeId(requestTypeId)
            .trackingNumber("AR-20260417-00002")
            .status(AdminRequestStatus.IN_PROGRESS)
            .urgencyLevel(UrgencyLevel.URGENT)
            .submittedAt(Instant.now())
            .build();

        when(approvalStepRepository.countByStatus(StepStatus.PENDING)).thenReturn(5L);
        when(adminRequestRepository.countByStatusIn(List.of(
            AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_PROGRESS))).thenReturn(6L);
        when(employeeRepository.count()).thenReturn(42L);
        when(departmentRepository.count()).thenReturn(5L);
        when(adminRequestRepository.findTop5ByStatusInOrderBySubmittedAtDesc(List.of(
            AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_PROGRESS))).thenReturn(List.of(adminRequest));
        when(adminRequestTypeRepository.findAllById(any()))
            .thenReturn(List.of(adminRequestType));

        HrDashboardDto result = dashboardService.getHrDashboard();

        assertThat(result.pendingApprovalsCount()).isEqualTo(5L);
        assertThat(result.pendingAdminRequestsCount()).isEqualTo(6L);
        assertThat(result.totalEmployees()).isEqualTo(42L);
        assertThat(result.totalDepartments()).isEqualTo(5L);
        assertThat(result.recentAdminRequests()).hasSize(1);
    }

    @Test
    @DisplayName("director dashboard returns aggregate counts")
    void directorDashboardReturnsAggregateCounts() {
        when(employeeRepository.count()).thenReturn(90L);
        when(departmentRepository.count()).thenReturn(8L);
        when(projectRepository.countByStatus(ProjectStatus.ACTIVE)).thenReturn(12L);
        when(approvalStepRepository.countByStatus(StepStatus.PENDING)).thenReturn(4L);
        when(adminRequestRepository.countByStatusIn(List.of(
            AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_PROGRESS))).thenReturn(3L);
        when(analyticsService.getLeaveMetrics(any())).thenReturn(
            new LeaveMetricsDto(15L, 11L, 1L, 3.0)
        );

        DirectorDashboardDto result = dashboardService.getDirectorDashboard();

        assertThat(result.totalEmployees()).isEqualTo(90L);
        assertThat(result.totalDepartments()).isEqualTo(8L);
        assertThat(result.activeProjectsCount()).isEqualTo(12L);
        assertThat(result.pendingApprovalsCount()).isEqualTo(4L);
        assertThat(result.pendingAdminRequestsCount()).isEqualTo(3L);
        assertThat(result.currentPeriodLeaveMetrics().period()).isEqualTo("LIVE");
        assertThat(result.currentPeriodLeaveMetrics().totalRequests()).isEqualTo(15);
        assertThat(result.currentPeriodLeaveMetrics().approvedCount()).isEqualTo(11);
        assertThat(result.currentPeriodLeaveMetrics().rejectedCount()).isEqualTo(1);
        assertThat(result.currentPeriodLeaveMetrics().avgProcessingDays()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("employee dashboard fails consistently when employee profile is missing")
    void employeeDashboardFailsWhenEmployeeProfileMissing() {
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getEmployeeDashboard(userId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Employee not found");
    }
}
