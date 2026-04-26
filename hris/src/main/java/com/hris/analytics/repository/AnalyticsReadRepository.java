package com.hris.analytics.repository;

import com.hris.auth.enums.EmployeeStatus;
import com.hris.leave.enums.LeaveStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface AnalyticsReadRepository extends Repository<com.hris.leave.entity.LeaveRequest, UUID> {

    interface LeaveMetricsCountsView {
        long getTotalRequests();
        long getApprovedCount();
        long getRejectedCount();
    }

    interface LeaveProcessingWindowView {
        Instant getSubmittedAt();
        Instant getCompletedAt();
    }

    interface HeadcountMetricsView {
        long getTotalEmployees();
        long getActiveEmployees();
        long getNewHires();
        long getTerminatedEmployees();
    }

    interface ProjectTeamSizeView {
        UUID getProjectId();
        String getProjectName();
        long getTeamSize();
    }

    interface ProjectAbsenceImpactView {
        UUID getProjectId();
        String getProjectName();
        long getTotalAbsenceDays();
        long getAffectedEmployeesCount();
    }

    interface LeaveTypeDistributionView {
        String getLeaveTypeCode();
        String getLeaveTypeName();
        long getRequestCount();
        long getTotalDays();
    }

    interface LeaveTrendView {
        Integer getYear();
        Integer getMonth();
        long getTotalRequests();
        long getApprovedCount();
        long getRejectedCount();
    }

    @Query("""
        SELECT
            COUNT(lr) AS totalRequests,
            COALESCE(SUM(CASE WHEN lr.status = :approvedStatus THEN 1 ELSE 0 END), 0) AS approvedCount,
            COALESCE(SUM(CASE WHEN lr.status = :rejectedStatus THEN 1 ELSE 0 END), 0) AS rejectedCount
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE lr.submittedAt >= :from
          AND lr.submittedAt < :to
          AND (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (
            :applyProjectScope = false OR EXISTS (
                SELECT 1 FROM ProjectAssignment pa
                WHERE pa.employeeId = e.id
                  AND pa.projectId IN :projectIds
                  AND pa.isActive = true
                  AND pa.startDate <= lr.endDate
                  AND (pa.endDate IS NULL OR pa.endDate >= lr.startDate)
            )
          )
        """)
    LeaveMetricsCountsView getLeaveMetricsCounts(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds,
        @Param("approvedStatus") LeaveStatus approvedStatus,
        @Param("rejectedStatus") LeaveStatus rejectedStatus
    );

    @Query("""
        SELECT
            lr.submittedAt AS submittedAt,
            aw.completedAt AS completedAt
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        JOIN ApprovalWorkflow aw ON aw.subjectId = lr.id AND aw.subjectType = 'LEAVE'
        WHERE lr.submittedAt >= :from
          AND lr.submittedAt < :to
          AND lr.status IN :completedStatuses
          AND aw.completedAt IS NOT NULL
          AND (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (
            :applyProjectScope = false OR EXISTS (
                SELECT 1 FROM ProjectAssignment pa
                WHERE pa.employeeId = e.id
                  AND pa.projectId IN :projectIds
                  AND pa.isActive = true
                  AND pa.startDate <= lr.endDate
                  AND (pa.endDate IS NULL OR pa.endDate >= lr.startDate)
            )
          )
        """)
    List<LeaveProcessingWindowView> findCompletedLeaveProcessingWindows(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds,
        @Param("completedStatuses") Collection<LeaveStatus> completedStatuses
    );

    @Query("""
        SELECT
            COUNT(e) AS totalEmployees,
            COALESCE(SUM(CASE WHEN e.status = :activeStatus THEN 1 ELSE 0 END), 0) AS activeEmployees,
            COALESCE(SUM(CASE WHEN e.hireDate >= :monthStart AND e.hireDate < :nextMonthStart THEN 1 ELSE 0 END), 0) AS newHires,
            COALESCE(SUM(CASE WHEN e.status = :terminatedStatus THEN 1 ELSE 0 END), 0) AS terminatedEmployees
        FROM Employee e
        WHERE (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (
            :applyProjectScope = false OR EXISTS (
                SELECT 1 FROM ProjectAssignment pa
                WHERE pa.employeeId = e.id
                  AND pa.projectId IN :projectIds
                  AND pa.isActive = true
                  AND pa.startDate <= :today
                  AND (pa.endDate IS NULL OR pa.endDate >= :today)
            )
          )
        """)
    HeadcountMetricsView getHeadcountMetrics(
        @Param("departmentId") UUID departmentId,
        @Param("monthStart") LocalDate monthStart,
        @Param("nextMonthStart") LocalDate nextMonthStart,
        @Param("today") LocalDate today,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds,
        @Param("activeStatus") EmployeeStatus activeStatus,
        @Param("terminatedStatus") EmployeeStatus terminatedStatus
    );

    @Query("""
        SELECT
            p.id AS projectId,
            p.name AS projectName,
            COUNT(DISTINCT pa.employeeId) AS teamSize
        FROM Project p
        LEFT JOIN ProjectAssignment pa
            ON pa.projectId = p.id
           AND pa.isActive = true
           AND pa.startDate <= :today
           AND (pa.endDate IS NULL OR pa.endDate >= :today)
        LEFT JOIN Employee e ON e.id = pa.employeeId
        WHERE (:departmentId IS NULL OR e.departmentId = :departmentId OR pa.id IS NULL)
          AND (:applyProjectScope = false OR p.id IN :projectIds)
        GROUP BY p.id, p.name
        ORDER BY p.name ASC
        """)
    List<ProjectTeamSizeView> findProjectTeamSizes(
        @Param("today") LocalDate today,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds
    );

    @Query("""
        SELECT
            p.id AS projectId,
            p.name AS projectName,
            COALESCE(SUM(lr.workingDays), 0) AS totalAbsenceDays,
            COUNT(DISTINCT lr.employeeId) AS affectedEmployeesCount
        FROM Project p
        JOIN ProjectAssignment pa
            ON pa.projectId = p.id
           AND pa.isActive = true
           AND pa.startDate <= :today
           AND (pa.endDate IS NULL OR pa.endDate >= :today)
        JOIN Employee e ON e.id = pa.employeeId
        JOIN LeaveRequest lr
            ON lr.employeeId = pa.employeeId
           AND lr.status = :approvedStatus
           AND lr.endDate >= :today
           AND lr.startDate <= COALESCE(pa.endDate, lr.endDate)
           AND lr.endDate >= pa.startDate
        WHERE (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (:applyProjectScope = false OR p.id IN :projectIds)
        GROUP BY p.id, p.name
        ORDER BY p.name ASC
        """)
    List<ProjectAbsenceImpactView> findProjectAbsenceImpact(
        @Param("today") LocalDate today,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds,
        @Param("approvedStatus") LeaveStatus approvedStatus
    );

    @Query("""
        SELECT
            lt.code AS leaveTypeCode,
            lt.name AS leaveTypeName,
            COUNT(lr) AS requestCount,
            COALESCE(SUM(lr.workingDays), 0) AS totalDays
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        JOIN LeaveType lt ON lt.id = lr.leaveTypeId
        WHERE lr.submittedAt >= :from
          AND lr.submittedAt < :to
          AND (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (
            :applyProjectScope = false OR EXISTS (
                SELECT 1 FROM ProjectAssignment pa
                WHERE pa.employeeId = e.id
                  AND pa.projectId IN :projectIds
                  AND pa.isActive = true
                  AND pa.startDate <= lr.endDate
                  AND (pa.endDate IS NULL OR pa.endDate >= lr.startDate)
            )
          )
        GROUP BY lt.code, lt.name
        ORDER BY COUNT(lr) DESC, lt.name ASC
        """)
    List<LeaveTypeDistributionView> getLeaveTypeDistribution(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds
    );

    @Query(value = """
        SELECT
            EXTRACT(YEAR FROM lr.submitted_at) AS year,
            EXTRACT(MONTH FROM lr.submitted_at) AS month,
            COUNT(*) AS totalRequests,
            COALESCE(SUM(CASE WHEN lr.status = CAST(:approvedStatus AS text) THEN 1 ELSE 0 END), 0) AS approvedCount,
            COALESCE(SUM(CASE WHEN lr.status = CAST(:rejectedStatus AS text) THEN 1 ELSE 0 END), 0) AS rejectedCount
        FROM leave_requests lr
        JOIN employees e ON e.id = lr.employee_id
        WHERE lr.submitted_at >= :from
          AND lr.submitted_at < :to
          AND (:departmentId IS NULL OR e.department_id = :departmentId)
          AND (
            :applyProjectScope = false OR EXISTS (
                SELECT 1 FROM project_assignments pa
                WHERE pa.employee_id = e.id
                  AND pa.project_id IN (:projectIds)
                  AND pa.is_active = true
                  AND pa.start_date <= lr.end_date
                  AND (pa.end_date IS NULL OR pa.end_date >= lr.start_date)
            )
          )
        GROUP BY EXTRACT(YEAR FROM lr.submitted_at), EXTRACT(MONTH FROM lr.submitted_at)
        ORDER BY year ASC, month ASC
        """, nativeQuery = true)
    List<LeaveTrendView> getLeaveTrend(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("departmentId") UUID departmentId,
        @Param("applyProjectScope") boolean applyProjectScope,
        @Param("projectIds") Collection<UUID> projectIds,
        @Param("approvedStatus") String approvedStatus,
        @Param("rejectedStatus") String rejectedStatus
    );
}
