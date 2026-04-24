package com.hris.analytics.service;

import com.hris.analytics.dto.AbsenceImpactDto;
import com.hris.analytics.dto.HeadcountMetricsDto;
import com.hris.analytics.dto.LeaveMetricsDto;
import com.hris.analytics.enums.RiskLevel;
import com.hris.analytics.enums.ScopeType;
import com.hris.analytics.repository.AnalyticsReadRepository;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.common.ScopeFilter;
import com.hris.leave.enums.LeaveStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsReadRepository analyticsReadRepository;

    @Transactional(readOnly = true)
    public LeaveMetricsDto getLeaveMetrics(ScopeFilter scope) {
        UUID departmentId = toDepartmentScope(scope);
        boolean applyProjectScope = scope.type() == ScopeType.PROJECT;
        List<UUID> projectIds = toProjectScope(scope);

        if (applyProjectScope && projectIds.isEmpty()) {
            return new LeaveMetricsDto(0, 0, 0, 0.0);
        }

        YearMonth currentMonth = YearMonth.now();
        Instant from = currentMonth.atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        Instant to = currentMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        AnalyticsReadRepository.LeaveMetricsCountsView counts = analyticsReadRepository.getLeaveMetricsCounts(
            from,
            to,
            departmentId,
            applyProjectScope,
            projectIds,
            LeaveStatus.APPROVED,
            LeaveStatus.REJECTED
        );

        List<AnalyticsReadRepository.LeaveProcessingWindowView> completedRequests =
            analyticsReadRepository.findCompletedLeaveProcessingWindows(
                from,
                to,
                departmentId,
                applyProjectScope,
                projectIds,
                EnumSet.of(LeaveStatus.APPROVED, LeaveStatus.REJECTED)
            );

        double averageProcessingDays = completedRequests.stream()
            .mapToLong(window -> computeDays(window.getSubmittedAt(), window.getCompletedAt()))
            .average()
            .orElse(0.0);

        return new LeaveMetricsDto(
            counts.getTotalRequests(),
            counts.getApprovedCount(),
            counts.getRejectedCount(),
            averageProcessingDays
        );
    }

    @Transactional(readOnly = true)
    public HeadcountMetricsDto getHeadcountMetrics(ScopeFilter scope) {
        UUID departmentId = toDepartmentScope(scope);
        boolean applyProjectScope = scope.type() == ScopeType.PROJECT;
        List<UUID> projectIds = toProjectScope(scope);

        if (applyProjectScope && projectIds.isEmpty()) {
            return new HeadcountMetricsDto(0, 0, 0, 0);
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate nextMonthStart = currentMonth.plusMonths(1).atDay(1);
        LocalDate today = LocalDate.now();

        AnalyticsReadRepository.HeadcountMetricsView view = analyticsReadRepository.getHeadcountMetrics(
            departmentId,
            monthStart,
            nextMonthStart,
            today,
            applyProjectScope,
            projectIds,
            EmployeeStatus.ACTIVE,
            EmployeeStatus.TERMINATED
        );

        return new HeadcountMetricsDto(
            view.getTotalEmployees(),
            view.getActiveEmployees(),
            view.getNewHires(),
            view.getTerminatedEmployees()
        );
    }

    @Transactional(readOnly = true)
    public List<AbsenceImpactDto> getAbsenceImpact(ScopeFilter scope) {
        UUID departmentId = toDepartmentScope(scope);
        LocalDate today = LocalDate.now();
        boolean applyProjectScope = scope.type() == ScopeType.PROJECT;
        List<UUID> projectIds = toProjectScope(scope);

        if (applyProjectScope && projectIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, AnalyticsReadRepository.ProjectAbsenceImpactView> impactByProject =
            analyticsReadRepository.findProjectAbsenceImpact(
                    today, departmentId, applyProjectScope, projectIds, LeaveStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(
                    AnalyticsReadRepository.ProjectAbsenceImpactView::getProjectId,
                    Function.identity()
                ));

        return analyticsReadRepository.findProjectTeamSizes(today, departmentId, applyProjectScope, projectIds).stream()
            .map(team -> {
                AnalyticsReadRepository.ProjectAbsenceImpactView impact = impactByProject.get(team.getProjectId());
                long totalAbsenceDays = impact != null ? impact.getTotalAbsenceDays() : 0L;
                long affectedEmployeesCount = impact != null ? impact.getAffectedEmployeesCount() : 0L;
                int estimatedDelayDays = team.getTeamSize() > 0
                    ? Math.toIntExact(totalAbsenceDays / team.getTeamSize())
                    : 0;

                return new AbsenceImpactDto(
                    team.getProjectId(),
                    team.getProjectName(),
                    Math.toIntExact(totalAbsenceDays),
                    Math.toIntExact(affectedEmployeesCount),
                    estimatedDelayDays,
                    toRiskLevel(estimatedDelayDays)
                );
            })
            .filter(row -> row.totalAbsenceDays() > 0 || row.affectedEmployeesCount() > 0 || row.estimatedDelayDays() > 0)
            .toList();
    }

    private UUID toDepartmentScope(ScopeFilter scope) {
        return scope.type() == ScopeType.DEPARTMENT ? scope.entityId() : null;
    }

    private List<UUID> toProjectScope(ScopeFilter scope) {
        return scope.type() == ScopeType.PROJECT ? scope.entityIds() : List.of();
    }

    private long computeDays(Instant submittedAt, Instant completedAt) {
        if (submittedAt == null || completedAt == null || completedAt.isBefore(submittedAt)) {
            return 0L;
        }

        return Math.max(1L, Duration.between(submittedAt, completedAt).toDays());
    }

    private RiskLevel toRiskLevel(int estimatedDelayDays) {
        if (estimatedDelayDays >= 6) {
            return RiskLevel.CRITICAL;
        }
        if (estimatedDelayDays >= 3) {
            return RiskLevel.HIGH;
        }
        if (estimatedDelayDays >= 1) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
