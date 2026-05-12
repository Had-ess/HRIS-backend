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
import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.dto.AnalyticsSummaryDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
            SELECT AVG(EXTRACT(EPOCH FROM (lr.updated_at - lr.submitted_at)) / 86400.0)
            FROM leave_requests lr
            JOIN employees e ON e.id = lr.employee_id
            WHERE %s
              AND lr.submitted_at >= :fromAt
              AND lr.submitted_at < :toExclusiveAt
              AND lr.submitted_at IS NOT NULL
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
}
