package com.hris.dashboard.service;

import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.dto.LeaveMetricsSnapshotDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.dashboard.dto.AdminRequestSummaryDto;
import com.hris.dashboard.dto.ApprovalSummaryDto;
import com.hris.dashboard.dto.DirectorDashboardDto;
import com.hris.dashboard.dto.EmployeeDashboardDto;
import com.hris.dashboard.dto.HrDashboardDto;
import com.hris.dashboard.dto.LeaveBalanceSummaryDto;
import com.hris.dashboard.dto.LeaveMetricsSummaryDto;
import com.hris.dashboard.dto.LeaveRequestSummaryDto;
import com.hris.dashboard.dto.SupervisorDashboardDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.repository.NotificationRepository;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<AdminRequestStatus> ACTIONABLE_ADMIN_REQUEST_STATUSES = List.of(
        AdminRequestStatus.SUBMITTED,
        AdminRequestStatus.IN_PROGRESS
    );

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AccessScopeService accessScopeService;
    private final AnalyticsQueryService analyticsQueryService;

    @Transactional(readOnly = true)
    public EmployeeDashboardDto getEmployeeDashboard(UUID userId) {
        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        List<LeaveBalance> leaveBalances = leaveBalanceRepository.findByEmployeeIdAndYear(
            employee.getId(), LocalDate.now().getYear());
        List<LeaveRequest> leaveRequests =
            leaveRequestRepository.findTop5ByEmployeeIdOrderBySubmittedAtDesc(employee.getId());
        List<AdminRequest> adminRequests =
            adminRequestRepository.findTop5ByRequesterIdOrderBySubmittedAtDesc(userId);

        return new EmployeeDashboardDto(
            notificationRepository.countByUserIdAndIsReadFalse(userId),
            mapLeaveBalances(leaveBalances),
            mapLeaveRequests(leaveRequests),
            mapAdminRequests(adminRequests)
        );
    }

    @Transactional(readOnly = true)
    public SupervisorDashboardDto getSupervisorDashboard(UUID userId) {
        List<ApprovalStep> pendingSteps =
            approvalStepRepository.findTop5ByApproverIdAndStatusOrderByStepOrderAsc(
                userId, StepStatus.PENDING);
        Employee employee = accessScopeService.getEmployeeOrThrow(userId);
        LocalDate today = LocalDate.now();

        long teamMembersCount;
        long upcomingTeamAbsencesCount;

        if (accessScopeService.hasProjectScopedManagement(userId)
            && !accessScopeService.resolveDepartmentManagerDepartmentId(userId, employee).isPresent()) {
            teamMembersCount = projectAssignmentRepository.countActiveDistinctEmployeesBySupervisorId(
                employee.getId(), today);
            upcomingTeamAbsencesCount = leaveRequestRepository.countUpcomingSupervisorRequests(
                employee.getId(), LeaveStatus.APPROVED, today);
        } else {
            teamMembersCount = employeeRepository.countByDepartmentId(employee.getDepartmentId());
            upcomingTeamAbsencesCount = leaveRequestRepository.countUpcomingDepartmentRequests(
                employee.getDepartmentId(), LeaveStatus.APPROVED, today);
        }

        return new SupervisorDashboardDto(
            approvalStepRepository.countByApproverIdAndStatus(userId, StepStatus.PENDING),
            mapApprovalSteps(pendingSteps),
            teamMembersCount,
            upcomingTeamAbsencesCount
        );
    }

    @Transactional(readOnly = true)
    public HrDashboardDto getHrDashboard() {
        return new HrDashboardDto(
            approvalStepRepository.countByStatus(StepStatus.PENDING),
            adminRequestRepository.countByStatusIn(ACTIONABLE_ADMIN_REQUEST_STATUSES),
            employeeRepository.count(),
            departmentRepository.count(),
            mapAdminRequests(
                adminRequestRepository.findTop5ByStatusInOrderBySubmittedAtDesc(
                    ACTIONABLE_ADMIN_REQUEST_STATUSES))
        );
    }

    @Transactional(readOnly = true)
    public DirectorDashboardDto getDirectorDashboard() {
        LocalDate today = LocalDate.now();
        LeaveMetricsSnapshotDto leaveMetrics = analyticsQueryService.getLeaveMetrics(
            today,
            AnalyticsScopeType.GLOBAL,
            null
        );

        return new DirectorDashboardDto(
            employeeRepository.count(),
            departmentRepository.count(),
            projectRepository.countByStatus(ProjectStatus.ACTIVE),
            approvalStepRepository.countByStatus(StepStatus.PENDING),
            new LeaveMetricsSummaryDto(
                today.toString(),
                leaveMetrics.totalRequests(),
                leaveMetrics.approvedCount(),
                leaveMetrics.rejectedCount(),
                leaveMetrics.averageProcessingDays().doubleValue()
            ),
            adminRequestRepository.countByStatusIn(ACTIONABLE_ADMIN_REQUEST_STATUSES)
        );
    }

    private List<LeaveBalanceSummaryDto> mapLeaveBalances(List<LeaveBalance> balances) {
        Map<UUID, String> leaveTypeNames = leaveTypeRepository.findAllById(
                balances.stream().map(LeaveBalance::getLeaveTypeId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));

        return balances.stream()
            .map(balance -> new LeaveBalanceSummaryDto(
                balance.getLeaveTypeId(),
                leaveTypeNames.get(balance.getLeaveTypeId()),
                balance.getTotalDays(),
                balance.getUsedDays(),
                balance.getPendingDays(),
                balance.getAvailableDays()
            ))
            .toList();
    }

    private List<LeaveRequestSummaryDto> mapLeaveRequests(List<LeaveRequest> requests) {
        Map<UUID, String> leaveTypeNames = leaveTypeRepository.findAllById(
                requests.stream().map(LeaveRequest::getLeaveTypeId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));

        return requests.stream()
            .map(request -> new LeaveRequestSummaryDto(
                request.getId(),
                request.getLeaveTypeId(),
                leaveTypeNames.get(request.getLeaveTypeId()),
                request.getStartDate(),
                request.getEndDate(),
                request.getWorkingDays(),
                request.getStatus(),
                request.getSubmittedAt()
            ))
            .toList();
    }

    private List<AdminRequestSummaryDto> mapAdminRequests(List<AdminRequest> requests) {
        Map<UUID, String> requestTypeNames = adminRequestTypeRepository.findAllById(
                requests.stream().map(AdminRequest::getRequestTypeId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(AdminRequestType::getId, AdminRequestType::getName));

        return requests.stream()
            .map(request -> new AdminRequestSummaryDto(
                request.getId(),
                request.getRequestTypeId(),
                requestTypeNames.get(request.getRequestTypeId()),
                request.getTrackingNumber(),
                request.getStatus(),
                request.getUrgencyLevel(),
                request.getSubmittedAt()
            ))
            .toList();
    }

    private List<ApprovalSummaryDto> mapApprovalSteps(List<ApprovalStep> approvalSteps) {
        Set<UUID> workflowIds = approvalSteps.stream()
            .map(ApprovalStep::getWorkflowId)
            .collect(Collectors.toSet());
        Map<UUID, ApprovalWorkflow> workflows = approvalWorkflowRepository.findAllById(workflowIds).stream()
            .collect(Collectors.toMap(ApprovalWorkflow::getId, Function.identity()));

        return approvalSteps.stream()
            .map(step -> {
                ApprovalWorkflow workflow = workflows.get(step.getWorkflowId());
                return new ApprovalSummaryDto(
                    step.getId(),
                    step.getWorkflowId(),
                    workflow != null ? workflow.getSubjectType() : null,
                    workflow != null ? workflow.getSubjectId() : null,
                    step.getContext(),
                    step.getStepOrder(),
                    step.getStatus(),
                    workflow != null ? workflow.getCreatedAt() : null
                );
            })
            .toList();
    }
}
