package com.hris.analytics.service;

import com.hris.analytics.dto.AnalyticsAdminRequestReportDto;
import com.hris.analytics.dto.AnalyticsApprovalBottleneckDto;
import com.hris.analytics.dto.AnalyticsApprovalReportDto;
import com.hris.analytics.dto.AnalyticsCountDto;
import com.hris.analytics.dto.AnalyticsDashboardDto;
import com.hris.analytics.dto.AnalyticsDateCountDto;
import com.hris.analytics.dto.AnalyticsLeaveBalanceBreakdownDto;
import com.hris.analytics.dto.AnalyticsLeaveBalanceReportDto;
import com.hris.analytics.dto.AnalyticsLeaveRequestReportDto;
import com.hris.analytics.dto.AnalyticsLeaveTypeOverviewDto;
import com.hris.analytics.dto.AnalyticsOverviewBreakdownDto;
import com.hris.analytics.dto.AnalyticsOverviewDto;
import com.hris.analytics.dto.AnalyticsOverviewKpiDto;
import com.hris.analytics.dto.AnalyticsOverviewSeriesPointDto;
import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.dto.AnalyticsSummaryDto;
import com.hris.analytics.dto.HeadcountMetricsSnapshotDto;
import com.hris.analytics.dto.HeadcountTrendPointDto;
import com.hris.analytics.dto.LeaveDistributionSnapshotDto;
import com.hris.analytics.dto.LeaveMonthlyStatusPointDto;
import com.hris.analytics.dto.LeaveMetricsSnapshotDto;
import com.hris.analytics.dto.LeaveMetricsTimeseriesPointDto;
import com.hris.analytics.dto.ProjectAbsenceFactDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private static final List<String> LEAVE_PENDING_STATUSES = List.of("PENDING", "IN_APPROVAL");
    private static final List<String> ADMIN_PENDING_STATUSES = List.of("SUBMITTED", "IN_REVIEW", "APPROVED");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsScopeService analyticsScopeService;

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        long leaveRequests = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            """.formatted(scope), params);

        long pendingLeaveRequests = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status IN (:pendingStatuses)
            """.formatted(scope), copy(params).addValue("pendingStatuses", LEAVE_PENDING_STATUSES));

        long pendingApprovals = singleLong("""
            SELECT COUNT(*)
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aps.status = 'PENDING'
            """.formatted(scope), params);

        long pendingAdminRequests = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status IN (:pendingStatuses)
            """.formatted(scope), copy(params).addValue("pendingStatuses", ADMIN_PENDING_STATUSES));

        long availableBalanceDays = singleLong("""
            SELECT COALESCE(SUM(lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scope), params);

        long negativeBalances = singleLong("""
            SELECT COUNT(*)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
              AND (lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days) < 0
            """.formatted(scope), params);

        long slaExceededAdminRequests = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.due_at IS NOT NULL
              AND (
                (ar.status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED') AND ar.due_at < NOW())
                OR (COALESCE(ar.completed_at, ar.decided_at) IS NOT NULL AND COALESCE(ar.completed_at, ar.decided_at) > ar.due_at)
              )
            """.formatted(scope), params);

        return new AnalyticsSummaryDto(
            leaveRequests,
            pendingLeaveRequests,
            pendingApprovals,
            pendingAdminRequests,
            availableBalanceDays,
            negativeBalances,
            slaExceededAdminRequests
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsLeaveRequestReportDto getLeaveRequests(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        long totalRequests = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            """.formatted(scope), params);

        long approvedCount = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status = 'APPROVED'
            """.formatted(scope), params);

        long rejectedCount = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status = 'REJECTED'
            """.formatted(scope), params);

        long pendingCount = singleLong("""
            SELECT COUNT(*)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status IN (:pendingStatuses)
            """.formatted(scope), copy(params).addValue("pendingStatuses", LEAVE_PENDING_STATUSES));

        double averageProcessingDays = singleDouble("""
            SELECT AVG(lfact.approval_duration_days)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            LEFT JOIN analytics_leave_facts lfact ON lfact.leave_request_id = lr.id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status IN ('APPROVED', 'REJECTED')
            """.formatted(scope), params);

        List<AnalyticsCountDto> byStatus = jdbcTemplate.query("""
            SELECT lr.status AS key, lr.status AS label, COUNT(*) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY lr.status
            ORDER BY COUNT(*) DESC, lr.status ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));

        List<AnalyticsCountDto> byLeaveType = jdbcTemplate.query("""
            SELECT lt.code AS key, lt.name AS label, COUNT(*) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            JOIN leave_types lt ON lt.id = lr.leave_type_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY lt.code, lt.name
            ORDER BY COUNT(*) DESC, lt.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));

        List<AnalyticsCountDto> byDepartment = jdbcTemplate.query("""
            SELECT d.code AS key, d.name AS label, COUNT(*) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            JOIN departments d ON d.id = e.department_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY d.code, d.name
            ORDER BY COUNT(*) DESC, d.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));

        List<AnalyticsDateCountDto> overTime = jdbcTemplate.query("""
            SELECT DATE(lr.submitted_at) AS bucket, COUNT(*) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY DATE(lr.submitted_at)
            ORDER BY bucket ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsDateCountDto(rs.getDate("bucket").toLocalDate(), rs.getLong("value")));

        return new AnalyticsLeaveRequestReportDto(
            totalRequests,
            approvedCount,
            rejectedCount,
            pendingCount,
            averageProcessingDays,
            byStatus,
            byLeaveType,
            byDepartment,
            overTime
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsLeaveBalanceReportDto getLeaveBalances(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        long availableDays = singleLong("""
            SELECT COALESCE(SUM(lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scope), params);

        long reservedDays = singleLong("""
            SELECT COALESCE(SUM(lb.pending_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scope), params);

        long usedDays = singleLong("""
            SELECT COALESCE(SUM(lb.used_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scope), params);

        long acquiredDays = singleLong("""
            SELECT COALESCE(SUM(CASE WHEN lbt.transaction_type = 'ACCRUAL' THEN lbt.amount ELSE 0 END), 0)
            FROM leave_balance_transactions lbt
            JOIN employees e ON e.id = lbt.employee_id
            WHERE %s
              AND lbt.occurred_at >= :fromAt
              AND lbt.occurred_at < :toExclusiveAt
            """.formatted(scope), params);

        long negativeBalanceCount = singleLong("""
            SELECT COUNT(*)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
              AND (lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days) < 0
            """.formatted(scope), params);

        long manualAdjustmentsCount = singleLong("""
            SELECT COUNT(*)
            FROM leave_balance_transactions lbt
            JOIN employees e ON e.id = lbt.employee_id
            WHERE %s
              AND lbt.occurred_at >= :fromAt
              AND lbt.occurred_at < :toExclusiveAt
              AND lbt.transaction_type = 'MANUAL_ADJUSTMENT'
            """.formatted(scope), params);

        List<AnalyticsLeaveBalanceBreakdownDto> byLeaveType = jdbcTemplate.query("""
            SELECT lt.id,
                   lt.code,
                   lt.name,
                   COALESCE(SUM(lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days), 0) AS available_days,
                   COALESCE(SUM(lb.pending_days), 0) AS reserved_days,
                   COALESCE(SUM(lb.used_days), 0) AS used_days,
                   COALESCE(SUM(CASE WHEN lbt.transaction_type = 'ACCRUAL' THEN lbt.amount ELSE 0 END), 0) AS acquired_days,
                   COUNT(lb.id) AS balance_count
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            JOIN leave_types lt ON lt.id = lb.leave_type_id
            LEFT JOIN leave_balance_transactions lbt
              ON lbt.employee_id = lb.employee_id
             AND lbt.leave_type_id = lb.leave_type_id
             AND lbt.occurred_at >= :fromAt
             AND lbt.occurred_at < :toExclusiveAt
            WHERE %s
              AND lb.year = :balanceYear
            GROUP BY lt.id, lt.code, lt.name
            ORDER BY lt.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsLeaveBalanceBreakdownDto(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getLong("available_days"),
                rs.getLong("reserved_days"),
                rs.getLong("used_days"),
                rs.getLong("acquired_days"),
                rs.getLong("balance_count")
            ));

        List<AnalyticsLeaveBalanceBreakdownDto> byDepartment = jdbcTemplate.query("""
            SELECT d.id,
                   d.code,
                   d.name,
                   COALESCE(SUM(lb.total_days + lb.carry_over_days - lb.used_days - lb.pending_days), 0) AS available_days,
                   COALESCE(SUM(lb.pending_days), 0) AS reserved_days,
                   COALESCE(SUM(lb.used_days), 0) AS used_days,
                   COALESCE(SUM(CASE WHEN lbt.transaction_type = 'ACCRUAL' THEN lbt.amount ELSE 0 END), 0) AS acquired_days,
                   COUNT(lb.id) AS balance_count
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            JOIN departments d ON d.id = e.department_id
            LEFT JOIN leave_balance_transactions lbt
              ON lbt.employee_id = lb.employee_id
             AND lbt.leave_type_id = lb.leave_type_id
             AND lbt.occurred_at >= :fromAt
             AND lbt.occurred_at < :toExclusiveAt
            WHERE %s
              AND lb.year = :balanceYear
            GROUP BY d.id, d.code, d.name
            ORDER BY d.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsLeaveBalanceBreakdownDto(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getLong("available_days"),
                rs.getLong("reserved_days"),
                rs.getLong("used_days"),
                rs.getLong("acquired_days"),
                rs.getLong("balance_count")
            ));

        return new AnalyticsLeaveBalanceReportDto(
            availableDays,
            reservedDays,
            usedDays,
            acquiredDays,
            negativeBalanceCount,
            manualAdjustmentsCount,
            byLeaveType,
            byDepartment
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsApprovalReportDto getApprovals(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        long workflowTotal = singleLong("""
            SELECT COUNT(*)
            FROM approval_workflows aw
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
            """.formatted(scope), params);

        long workflowPending = singleLong("""
            SELECT COUNT(*)
            FROM approval_workflows aw
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aw.status IN ('PENDING', 'IN_PROGRESS')
            """.formatted(scope), params);

        long workflowApproved = singleLong("""
            SELECT COUNT(*)
            FROM approval_workflows aw
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aw.status = 'APPROVED'
            """.formatted(scope), params);

        long workflowRejected = singleLong("""
            SELECT COUNT(*)
            FROM approval_workflows aw
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aw.status = 'REJECTED'
            """.formatted(scope), params);

        long stepPending = singleLong("""
            SELECT COUNT(*)
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aps.status = 'PENDING'
            """.formatted(scope), params);

        long stepApproved = singleLong("""
            SELECT COUNT(*)
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aps.status = 'APPROVED'
            """.formatted(scope), params);

        long stepRejected = singleLong("""
            SELECT COUNT(*)
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aps.status = 'REJECTED'
            """.formatted(scope), params);

        double averageApprovalHours = singleDouble("""
            SELECT AVG(EXTRACT(EPOCH FROM (aw.completed_at - aw.created_at)) / 3600.0)
            FROM approval_workflows aw
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
              AND aw.completed_at IS NOT NULL
            """.formatted(scope), params);

        List<AnalyticsApprovalBottleneckDto> bottlenecks = jdbcTemplate.query("""
            SELECT aps.approver_id AS approver_user_id,
                   COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''), u.email, 'Unknown') AS approver_name,
                   SUM(CASE WHEN aps.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                   AVG(CASE WHEN aps.decided_at IS NOT NULL THEN EXTRACT(EPOCH FROM (aps.decided_at - aw.created_at)) / 3600.0 END) AS average_decision_hours,
                   SUM(CASE WHEN aps.status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            LEFT JOIN users u ON u.id = aps.approver_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
            GROUP BY aps.approver_id, approver_name
            ORDER BY pending_count DESC, average_decision_hours DESC NULLS LAST, approver_name ASC
            LIMIT 10
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsApprovalBottleneckDto(
                rs.getObject("approver_user_id", UUID.class),
                rs.getString("approver_name"),
                rs.getLong("pending_count"),
                rs.getDouble("average_decision_hours"),
                rs.getLong("rejected_count")
            ));

        return new AnalyticsApprovalReportDto(
            workflowTotal,
            workflowPending,
            workflowApproved,
            workflowRejected,
            stepPending,
            stepApproved,
            stepRejected,
            averageApprovalHours,
            0,
            bottlenecks
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsAdminRequestReportDto getAdminRequests(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        long totalRequests = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
            """.formatted(scope), params);

        long submittedCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status = 'SUBMITTED'
            """.formatted(scope), params);

        long inReviewCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status = 'IN_REVIEW'
            """.formatted(scope), params);

        long completedCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status = 'COMPLETED'
            """.formatted(scope), params);

        long rejectedCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status = 'REJECTED'
            """.formatted(scope), params);

        long pendingCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED')
            """.formatted(scope), params);

        double averageProcessingHours = singleDouble("""
            SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(ar.completed_at, ar.decided_at) - ar.submitted_at)) / 3600.0)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.submitted_at IS NOT NULL
              AND COALESCE(ar.completed_at, ar.decided_at) IS NOT NULL
            """.formatted(scope), params);

        long slaExceededCount = singleLong("""
            SELECT COUNT(*)
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
              AND ar.due_at IS NOT NULL
              AND (
                (ar.status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED') AND ar.due_at < NOW())
                OR (COALESCE(ar.completed_at, ar.decided_at) IS NOT NULL AND COALESCE(ar.completed_at, ar.decided_at) > ar.due_at)
              )
            """.formatted(scope), params);

        List<AnalyticsCountDto> byStatus = jdbcTemplate.query("""
            SELECT ar.status AS key, ar.status AS label, COUNT(*) AS value
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
            GROUP BY ar.status
            ORDER BY COUNT(*) DESC, ar.status ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));

        List<AnalyticsCountDto> byType = jdbcTemplate.query("""
            SELECT art.code AS key, art.name AS label, COUNT(*) AS value
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            JOIN admin_request_types art ON art.id = ar.type_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
            GROUP BY art.code, art.name
            ORDER BY COUNT(*) DESC, art.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));

        List<AnalyticsDateCountDto> overTime = jdbcTemplate.query("""
            SELECT DATE(COALESCE(ar.submitted_at, ar.created_at)) AS bucket, COUNT(*) AS value
            FROM admin_requests ar
            JOIN employees e ON e.id = ar.requester_employee_id
            WHERE %s
              AND ar.created_at >= :fromAt
              AND ar.created_at < :toExclusiveAt
            GROUP BY DATE(COALESCE(ar.submitted_at, ar.created_at))
            ORDER BY bucket ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new AnalyticsDateCountDto(rs.getDate("bucket").toLocalDate(), rs.getLong("value")));

        return new AnalyticsAdminRequestReportDto(
            totalRequests,
            submittedCount,
            inReviewCount,
            completedCount,
            rejectedCount,
            pendingCount,
            averageProcessingHours,
            slaExceededCount,
            byStatus,
            byType,
            overTime
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardDto getDashboard(UUID userId, AnalyticsScopeType scopeType, UUID scopeId, LocalDate from, LocalDate to) {
        AnalyticsScopeOptionDto scope = scopeType == null
            ? analyticsScopeService.getDefaultScope(userId)
            : new AnalyticsScopeOptionDto(scopeType, scopeId, resolveScopeLabel(userId, scopeType, scopeId));

        AnalyticsSummaryDto summary = getSummary(from, to, scope.scopeType(), scope.scopeId());
        MapSqlParameterSource params = params(scope.scopeType(), scope.scopeId(), normalizeFrom(from, to), normalizeTo(from, to));
        long employeesInScope = singleLong("""
            SELECT COUNT(DISTINCT e.id)
            FROM employees e
            WHERE %s
            """.formatted(scopeCondition(scope.scopeType(), "e.id")), params);

        return new AnalyticsDashboardDto(
            scope.scopeType(),
            scope.scopeId(),
            scope.label(),
            employeesInScope,
            summary.leaveRequests(),
            summary.pendingApprovals(),
            summary.pendingAdminRequests(),
            summary.availableBalanceDays()
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewDto getOverview(UUID userId, AnalyticsScopeType scopeType, UUID scopeId, LocalDate from, LocalDate to) {
        AnalyticsScopeOptionDto scope = scopeType == null
            ? analyticsScopeService.getDefaultScope(userId)
            : new AnalyticsScopeOptionDto(scopeType, scopeId, resolveScopeLabel(userId, scopeType, scopeId));

        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scope.scopeType(), scope.scopeId(), normalizedFrom, normalizedTo);
        String scopeClause = scopeCondition(scope.scopeType(), "e.id");

        long headcount = singleLong("""
            SELECT COUNT(DISTINCT e.id)
            FROM employees e
            WHERE %s
              AND e.hire_date <= :toDate
              AND (e.termination_date IS NULL OR e.termination_date >= :fromDate)
            """.formatted(scopeClause),
            copy(params).addValue("fromDate", normalizedFrom).addValue("toDate", normalizedTo));

        List<AnalyticsOverviewSeriesPointDto> headcountTrend = buildHeadcountOverviewTrend(
            scope.scopeType(),
            scope.scopeId(),
            normalizedTo.minusMonths(11).withDayOfMonth(1),
            normalizedTo
        );

        long totalLeaveDays = singleLong("""
            SELECT COALESCE(SUM(lr.working_days), 0)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status <> 'CANCELLED'
            """.formatted(scopeClause), params);

        long openRequests = singleLong("""
            SELECT COUNT(*)
            FROM (
                SELECT lr.id
                FROM leave_requests lr
                JOIN employees e ON e.id = lr.employee_id
                WHERE %s
                  AND lr.submitted_at >= :fromAt
                  AND lr.submitted_at < :toExclusiveAt
                  AND lr.status IN ('PENDING', 'IN_APPROVAL')
                UNION ALL
                SELECT ar.id
                FROM admin_requests ar
                JOIN employees e ON e.id = ar.requester_employee_id
                WHERE %s
                  AND ar.created_at >= :fromAt
                  AND ar.created_at < :toExclusiveAt
                  AND ar.status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED')
            ) open_items
            """.formatted(scopeClause, scopeClause), params);

        long annualQuotaDays = singleLong("""
            SELECT COALESCE(SUM(lb.total_days + lb.carry_over_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scopeClause), params);
        long usedAndReservedDays = singleLong("""
            SELECT COALESCE(SUM(lb.used_days + lb.pending_days), 0)
            FROM leave_balances lb
            JOIN employees e ON e.id = lb.employee_id
            WHERE %s
              AND lb.year = :balanceYear
            """.formatted(scopeClause), params);
        int utilization = annualQuotaDays <= 0 ? 0 : (int) Math.round((usedAndReservedDays * 100.0) / annualQuotaDays);

        List<AnalyticsLeaveTypeOverviewDto> leaveByType = buildLeaveTypeOverview(params, scopeClause);
        List<AnalyticsOverviewBreakdownDto> bottlenecks = buildApprovalOverview(params, scopeClause);
        List<AnalyticsOverviewBreakdownDto> leaveReasons = buildLeaveReasonOverview(params, scopeClause);

        return new AnalyticsOverviewDto(
            scope.scopeType(),
            scope.scopeId(),
            scope.label(),
            List.of(
                new AnalyticsOverviewKpiDto("headcount", "Headcount", Long.toString(headcount), trendDelta(headcountTrend), "people", "positive"),
                new AnalyticsOverviewKpiDto("totalLeaveDays", "Total leave days", Long.toString(totalLeaveDays), "YTD", "calendar", "neutral"),
                new AnalyticsOverviewKpiDto("averageUtilization", "Avg utilization", utilization + "%", "of annual quota", "briefcase", "neutral"),
                new AnalyticsOverviewKpiDto("openRequests", "Open requests", Long.toString(openRequests), "+ " + openRequests, "inbox", "positive")
            ),
            headcountTrend,
            leaveByType,
            bottlenecks,
            leaveReasons
        );
    }

    @Transactional(readOnly = true)
    public LeaveMetricsSnapshotDto getLeaveMetrics(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        AnalyticsLeaveRequestReportDto report = getLeaveRequests(from, to, scopeType, scopeId);
        return new LeaveMetricsSnapshotDto(
            (int) report.totalRequests(),
            (int) report.approvedCount(),
            (int) report.rejectedCount(),
            (int) report.pendingCount(),
            java.math.BigDecimal.valueOf(report.averageProcessingDays()).setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Transactional(readOnly = true)
    public HeadcountMetricsSnapshotDto getHeadcountMetrics(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        int totalEmployees = (int) singleLong("""
            SELECT COUNT(*)
            FROM employees e
            WHERE %s
            """.formatted(scope), params);

        int activeEmployees = (int) singleLong("""
            SELECT COUNT(*)
            FROM employees e
            WHERE %s
              AND e.status = 'ACTIVE'
            """.formatted(scope), params);

        int newHires = (int) singleLong("""
            SELECT COUNT(*)
            FROM employees e
            WHERE %s
              AND e.hire_date >= :fromDate
              AND e.hire_date <= :toDate
            """.formatted(scope), copy(params)
            .addValue("fromDate", normalizedFrom)
            .addValue("toDate", normalizedTo));

        int terminatedEmployees = (int) singleLong("""
            SELECT COUNT(*)
            FROM employees e
            WHERE %s
              AND e.termination_date IS NOT NULL
              AND e.termination_date >= :fromDate
              AND e.termination_date <= :toDate
            """.formatted(scope), copy(params)
            .addValue("fromDate", normalizedFrom)
            .addValue("toDate", normalizedTo));

        return new HeadcountMetricsSnapshotDto(totalEmployees, activeEmployees, newHires, terminatedEmployees);
    }

    @Transactional(readOnly = true)
    public List<LeaveDistributionSnapshotDto> getLeaveDistribution(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        return jdbcTemplate.query("""
            SELECT lt.id,
                   lt.code,
                   lt.name,
                   COUNT(*) AS request_count,
                   COALESCE(SUM(lr.working_days), 0) AS total_days
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            JOIN leave_types lt ON lt.id = lr.leave_type_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY lt.id, lt.code, lt.name
            ORDER BY request_count DESC, lt.name ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new LeaveDistributionSnapshotDto(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("request_count"),
                rs.getInt("total_days")
            ));
    }

    @Transactional(readOnly = true)
    public List<LeaveMetricsTimeseriesPointDto> getLeaveMetricsTimeseries(LocalDate from, LocalDate to, AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = params(scopeType, scopeId, normalizedFrom, normalizedTo);
        String scope = scopeCondition(scopeType, "e.id");

        return jdbcTemplate.query("""
            SELECT DATE(lr.submitted_at) AS snapshot_date,
                   COUNT(*) AS total_requests,
                   SUM(CASE WHEN lr.status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count,
                   SUM(CASE WHEN lr.status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
                   SUM(CASE WHEN lr.status IN ('PENDING', 'IN_APPROVAL') THEN 1 ELSE 0 END) AS pending_count,
                   AVG(CASE WHEN lr.status IN ('APPROVED', 'REJECTED') THEN lfact.approval_duration_days ELSE NULL END) AS average_processing_days
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            LEFT JOIN analytics_leave_facts lfact ON lfact.leave_request_id = lr.id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY DATE(lr.submitted_at)
            ORDER BY snapshot_date ASC
            """.formatted(scope), params, (rs, rowNum) ->
            new LeaveMetricsTimeseriesPointDto(
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getInt("total_requests"),
                rs.getInt("approved_count"),
                rs.getInt("rejected_count"),
                rs.getInt("pending_count"),
                java.math.BigDecimal.valueOf(rs.getDouble("average_processing_days")).setScale(2, java.math.RoundingMode.HALF_UP)
            ));
    }

    @Transactional(readOnly = true)
    public List<ProjectAbsenceFactDto> getProjectAbsenceFacts(AnalyticsScopeType scopeType, UUID scopeId) {
        LocalDate snapshotDate = latestProjectAbsenceSnapshotDate();
        if (snapshotDate == null) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("snapshotDate", snapshotDate);
        if (scopeId != null) {
            params.addValue("scopeId", scopeId);
        }

        String scopeClause = switch (scopeType) {
            case GLOBAL -> "1 = 1";
            case PROJECT -> "paf.project_id = :scopeId";
            case TEAM -> "paf.team_id = :scopeId";
            case DEPARTMENT -> """
                EXISTS (
                    SELECT 1
                    FROM project_departments pd
                    WHERE pd.project_id = paf.project_id
                      AND pd.department_id = :scopeId
                )
                """;
            case EMPLOYEE -> """
                EXISTS (
                    SELECT 1
                    FROM project_assignments pa
                    WHERE pa.project_id = paf.project_id
                      AND pa.employee_id = :scopeId
                      AND pa.is_active = true
                      AND pa.start_date <= CURRENT_DATE
                      AND (pa.end_date IS NULL OR pa.end_date >= CURRENT_DATE)
                )
                """;
        };

        return jdbcTemplate.query("""
            SELECT paf.project_id,
                   COALESCE(NULLIF(TRIM(p.name), ''), p.code, 'Project') AS project_name,
                   paf.team_id,
                   paf.absent_employees,
                   paf.absence_days,
                   paf.affected_members,
                   paf.estimated_delay_days,
                   paf.risk_level
            FROM analytics_project_absence_facts paf
            JOIN projects p ON p.id = paf.project_id
            WHERE paf.snapshot_date = :snapshotDate
              AND %s
            ORDER BY paf.estimated_delay_days DESC, project_name ASC
            """.formatted(scopeClause), params, (rs, rowNum) ->
            new ProjectAbsenceFactDto(
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("team_id", UUID.class),
                rs.getInt("absent_employees"),
                rs.getInt("absence_days"),
                rs.getInt("affected_members"),
                rs.getInt("estimated_delay_days"),
                com.hris.analytics.enums.RiskLevel.valueOf(rs.getString("risk_level"))
            ));
    }

    @Transactional(readOnly = true)
    public List<HeadcountTrendPointDto> getHeadcountTrend(int year) {
        List<HeadcountTrendPointDto> result = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate firstDay = LocalDate.of(year, month, 1);
            LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            long count = singleLong("""
                SELECT COUNT(*)
                FROM employees e
                JOIN users u ON u.id = e.user_id
                WHERE e.hire_date <= :lastDay
                  AND (e.termination_date IS NULL OR e.termination_date >= :firstDay)
                  AND u.is_active = true
                """,
                new MapSqlParameterSource()
                    .addValue("firstDay", firstDay)
                    .addValue("lastDay", lastDay));
            result.add(new HeadcountTrendPointDto(month, count));
        }
        return result;
    }

    private List<AnalyticsOverviewSeriesPointDto> buildHeadcountOverviewTrend(
            AnalyticsScopeType scopeType,
            UUID scopeId,
            LocalDate firstMonth,
            LocalDate to) {
        return IntStream.range(0, 12)
            .mapToObj(firstMonth::plusMonths)
            .map(month -> {
                LocalDate firstDay = month.withDayOfMonth(1);
                LocalDate lastDay = month.withDayOfMonth(month.lengthOfMonth());
                MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("firstDay", firstDay)
                    .addValue("lastDay", lastDay)
                    .addValue("toExclusiveAt", Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .addValue("fromAt", Timestamp.from(firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .addValue("balanceYear", to.getYear());
                if (scopeId != null) {
                    params.addValue("scopeId", scopeId);
                }
                long count = singleLong("""
                    SELECT COUNT(DISTINCT e.id)
                    FROM employees e
                    JOIN users u ON u.id = e.user_id
                    WHERE %s
                      AND e.hire_date <= :lastDay
                      AND (e.termination_date IS NULL OR e.termination_date >= :firstDay)
                      AND u.is_active = true
                    """.formatted(scopeCondition(scopeType, "e.id")), params);
                return new AnalyticsOverviewSeriesPointDto(monthLabel(month), count);
            })
            .toList();
    }

    private List<AnalyticsLeaveTypeOverviewDto> buildLeaveTypeOverview(MapSqlParameterSource params, String scopeClause) {
        List<AnalyticsCountDto> rows = jdbcTemplate.query("""
            SELECT COALESCE(lt.code, lt.name) AS key,
                   lt.name AS label,
                   COALESCE(SUM(lr.working_days), 0) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            JOIN leave_types lt ON lt.id = lr.leave_type_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.status <> 'CANCELLED'
            GROUP BY lt.code, lt.name
            ORDER BY value DESC, lt.name ASC
            LIMIT 6
            """.formatted(scopeClause), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));
        long total = rows.stream().mapToLong(AnalyticsCountDto::value).sum();
        List<String> colors = List.of("#2f75dc", "#f59e0b", "#0ea5e9", "#38bdf8", "#22c55e", "#64748b");
        return IntStream.range(0, rows.size())
            .mapToObj(i -> new AnalyticsLeaveTypeOverviewDto(
                rows.get(i).key(),
                rows.get(i).label(),
                rows.get(i).value(),
                percentage(rows.get(i).value(), total),
                colors.get(i % colors.size())
            ))
            .toList();
    }

    private List<AnalyticsOverviewBreakdownDto> buildApprovalOverview(MapSqlParameterSource params, String scopeClause) {
        List<AnalyticsApprovalBottleneckDto> rows = jdbcTemplate.query("""
            SELECT aps.approver_id AS approver_user_id,
                   COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''), u.email, 'Unassigned') AS approver_name,
                   SUM(CASE WHEN aps.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                   AVG(CASE WHEN aps.decided_at IS NOT NULL THEN EXTRACT(EPOCH FROM (aps.decided_at - aw.created_at)) / 3600.0 END) AS average_decision_hours,
                   SUM(CASE WHEN aps.status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count
            FROM approval_steps aps
            JOIN approval_workflows aw ON aw.id = aps.workflow_id
            JOIN leave_requests lr ON aw.subject_type = 'LEAVE_REQUEST' AND aw.subject_id = lr.id
            JOIN employees e ON e.id = lr.employee_id
            LEFT JOIN users u ON u.id = aps.approver_id
            WHERE %s
              AND aw.created_at >= :fromAt
              AND aw.created_at < :toExclusiveAt
            GROUP BY aps.approver_id, approver_name
            ORDER BY pending_count DESC, average_decision_hours DESC NULLS LAST, approver_name ASC
            LIMIT 4
            """.formatted(scopeClause), params, (rs, rowNum) ->
            new AnalyticsApprovalBottleneckDto(
                rs.getObject("approver_user_id", UUID.class),
                rs.getString("approver_name"),
                rs.getLong("pending_count"),
                rs.getDouble("average_decision_hours"),
                rs.getLong("rejected_count")
            ));
        long max = Math.max(1, rows.stream().mapToLong(AnalyticsApprovalBottleneckDto::pendingCount).max().orElse(1));
        return rows.stream()
            .map(row -> new AnalyticsOverviewBreakdownDto(
                row.approverUserId() == null ? row.approverName() : row.approverUserId().toString(),
                row.approverName(),
                row.pendingCount(),
                percentage(row.pendingCount(), max),
                String.format(java.util.Locale.US, "%.1fh avg", row.averageDecisionHours())
            ))
            .toList();
    }

    private List<AnalyticsOverviewBreakdownDto> buildLeaveReasonOverview(MapSqlParameterSource params, String scopeClause) {
        List<AnalyticsCountDto> rows = jdbcTemplate.query("""
            SELECT LOWER(COALESCE(NULLIF(TRIM(lr.comment), ''), lt.name)) AS key,
                   COALESCE(NULLIF(TRIM(lr.comment), ''), lt.name) AS label,
                   COUNT(*) AS value
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            JOIN leave_types lt ON lt.id = lr.leave_type_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
            GROUP BY key, label
            ORDER BY value DESC, label ASC
            LIMIT 4
            """.formatted(scopeClause), params, (rs, rowNum) ->
            new AnalyticsCountDto(rs.getString("key"), rs.getString("label"), rs.getLong("value")));
        long total = rows.stream().mapToLong(AnalyticsCountDto::value).sum();
        return rows.stream()
            .map(row -> new AnalyticsOverviewBreakdownDto(
                row.key(),
                row.label(),
                row.value(),
                percentage(row.value(), total),
                row.value() + " requests"
            ))
            .toList();
    }

    private int percentage(long value, long total) {
        return total <= 0 ? 0 : (int) Math.round((value * 100.0) / total);
    }

    private String trendDelta(List<AnalyticsOverviewSeriesPointDto> trend) {
        if (trend.size() < 2) {
            return "+0% YoY";
        }
        long first = trend.get(0).value();
        long last = trend.get(trend.size() - 1).value();
        int delta = first <= 0 ? 0 : (int) Math.round(((last - first) * 100.0) / first);
        return (delta >= 0 ? "+ " : "- ") + Math.abs(delta) + "% YoY";
    }

    private String monthLabel(LocalDate month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
    }

    @Transactional(readOnly = true)
    public List<LeaveMonthlyStatusPointDto> getLeaveMonthlyStatus(int year) {
        record Row(int month, String status, long count) {}
        List<Row> rows = jdbcTemplate.query("""
            SELECT EXTRACT(MONTH FROM created_at)::int AS month,
                   status,
                   COUNT(*) AS count
            FROM leave_requests
            WHERE EXTRACT(YEAR FROM created_at) = :year
            GROUP BY month, status
            ORDER BY month
            """,
            new MapSqlParameterSource().addValue("year", year),
            (rs, rowNum) -> new Row(rs.getInt("month"), rs.getString("status"), rs.getLong("count")));

        java.util.Map<Integer, long[]> byMonth = new java.util.TreeMap<>();
        for (Row row : rows) {
            byMonth.computeIfAbsent(row.month(), k -> new long[3]);
            long[] counts = byMonth.get(row.month());
            String s = row.status();
            if ("APPROVED".equals(s)) counts[0] += row.count();
            else if ("REJECTED".equals(s)) counts[1] += row.count();
            else counts[2] += row.count();
        }
        return byMonth.entrySet().stream()
            .map(e -> new LeaveMonthlyStatusPointDto(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
            .toList();
    }

    private String resolveScopeLabel(UUID userId, AnalyticsScopeType scopeType, UUID scopeId) {
        return analyticsScopeService.getAvailableScopes(userId).stream()
            .filter(option -> option.scopeType() == scopeType && Objects.equals(option.scopeId(), scopeId))
            .map(AnalyticsScopeOptionDto::label)
            .findFirst()
            .orElse(scopeType.name());
    }

    private String scopeCondition(AnalyticsScopeType scopeType, String employeeColumn) {
        return switch (scopeType) {
            case GLOBAL -> "1 = 1";
            case EMPLOYEE -> employeeColumn + " = :scopeId";
            case DEPARTMENT -> "e.department_id = :scopeId";
            case TEAM -> """
                EXISTS (
                    SELECT 1
                    FROM project_assignments pa
                    WHERE pa.employee_id = %s
                      AND pa.team_id = :scopeId
                      AND pa.is_active = true
                      AND pa.start_date <= CURRENT_DATE
                      AND (pa.end_date IS NULL OR pa.end_date >= CURRENT_DATE)
                )
                """.formatted(employeeColumn);
            case PROJECT -> "1 = 0";
        };
    }

    private MapSqlParameterSource params(AnalyticsScopeType scopeType, UUID scopeId, LocalDate from, LocalDate to) {
        LocalDate normalizedFrom = normalizeFrom(from, to);
        LocalDate normalizedTo = normalizeTo(from, to);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromAt", Timestamp.from(normalizedFrom.atStartOfDay().toInstant(ZoneOffset.UTC)))
            .addValue("toExclusiveAt", Timestamp.from(normalizedTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
            .addValue("balanceYear", normalizedTo.getYear());
        if (scopeId != null) {
            params.addValue("scopeId", scopeId);
        }
        return params;
    }

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        return new MapSqlParameterSource(source.getValues());
    }

    private long singleLong(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private double singleDouble(String sql, MapSqlParameterSource params) {
        Double value = jdbcTemplate.queryForObject(sql, params, Double.class);
        return value == null ? 0.0 : value;
    }

    private LocalDate normalizeFrom(LocalDate from, LocalDate to) {
        if (from != null) {
            return from;
        }
        if (to != null) {
            return to.withDayOfMonth(1);
        }
        return LocalDate.now().minusDays(29);
    }

    private LocalDate normalizeTo(LocalDate from, LocalDate to) {
        if (to != null) {
            return !to.isBefore(normalizeFrom(from, to)) ? to : normalizeFrom(from, to);
        }
        return LocalDate.now();
    }

    private LocalDate latestProjectAbsenceSnapshotDate() {
        return jdbcTemplate.query(
            "SELECT MAX(snapshot_date) AS snapshot_date FROM analytics_project_absence_facts",
            rs -> rs.next() ? rs.getObject("snapshot_date", LocalDate.class) : null
        );
    }
}
