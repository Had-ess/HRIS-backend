package com.hris.approval.repository;

import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.StepStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    boolean existsByApproverId(UUID approverId);

    List<ApprovalStep> findByWorkflowId(UUID workflowId);

    List<ApprovalStep> findByWorkflowIdOrderByStepOrder(UUID workflowId);

    List<ApprovalStep> findByWorkflowIdInOrderByWorkflowIdAscStepOrderAsc(List<UUID> workflowIds);

    List<ApprovalStep> findByWorkflowIdAndStatus(UUID workflowId, StepStatus status);

    List<ApprovalStep> findByApproverIdAndStatus(UUID approverId, StepStatus status);

    List<ApprovalStep> findByStatus(StepStatus status);

    Page<ApprovalStep> findByApproverIdAndStatusOrderByStepOrderAsc(
        UUID approverId, StepStatus status, Pageable pageable);

    List<ApprovalStep> findTop5ByApproverIdAndStatusOrderByStepOrderAsc(
        UUID approverId, StepStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ApprovalStep s WHERE s.id = :id")
    Optional<ApprovalStep> findByIdForUpdate(@Param("id") UUID id);


    long countByWorkflowIdAndStatus(UUID workflowId, StepStatus status);

    long countByApproverIdAndStatus(UUID approverId, StepStatus status);

    long countByStatus(StepStatus status);

    boolean existsByWorkflowIdAndStatus(UUID workflowId, StepStatus status);

    @Query(value = """
        SELECT s.step_order,
               COALESCE(AVG(EXTRACT(EPOCH FROM (s.decided_at - w.created_at)) / 3600.0), 0) AS avg_hours,
               COALESCE(
                 PERCENTILE_CONT(0.5) WITHIN GROUP (
                   ORDER BY EXTRACT(EPOCH FROM (s.decided_at - w.created_at)) / 3600.0
                 ), 0) AS median_hours,
               (SELECT COUNT(*) FROM approval_steps p
                WHERE p.step_order = s.step_order AND p.status = 'PENDING') AS pending_count
        FROM approval_steps s
        JOIN approval_workflows w ON w.id = s.workflow_id
        WHERE s.status IN ('APPROVED', 'REJECTED')
          AND s.decided_at IS NOT NULL
          AND s.decided_at >= :from
          AND s.decided_at < :toExclusive
        GROUP BY s.step_order
        ORDER BY s.step_order
        """, nativeQuery = true)
    List<Object[]> findCompletedStepTimingStats(
        @Param("from") java.time.Instant from,
        @Param("toExclusive") java.time.Instant toExclusive);

    @Query(value = """
        SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (s.decided_at - w.created_at)) / 3600.0), 0)
        FROM approval_steps s
        JOIN approval_workflows w ON w.id = s.workflow_id
        WHERE s.status IN ('APPROVED', 'REJECTED')
          AND s.decided_at IS NOT NULL
          AND s.decided_at >= :from
          AND s.decided_at < :toExclusive
        """, nativeQuery = true)
    double averageStepDecisionHoursBetween(
        @Param("from") java.time.Instant from,
        @Param("toExclusive") java.time.Instant toExclusive);

    @Query(value = """
        SELECT s.id, s.step_order, s.approver_id, w.created_at, w.subject_type, w.subject_id
        FROM approval_steps s
        JOIN approval_workflows w ON w.id = s.workflow_id
        WHERE s.status = 'PENDING'
          AND w.created_at < :olderThan
        ORDER BY w.created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findStalePendingSteps(
        @Param("olderThan") java.time.Instant olderThan,
        @Param("limit") int limit);
}
