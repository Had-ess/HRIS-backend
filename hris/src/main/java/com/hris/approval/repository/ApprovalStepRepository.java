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
}
