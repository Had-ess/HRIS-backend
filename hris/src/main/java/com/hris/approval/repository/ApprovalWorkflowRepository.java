package com.hris.approval.repository;

import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.WorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM ApprovalWorkflow w WHERE w.id = :id")
    Optional<ApprovalWorkflow> findByIdForUpdate(@Param("id") UUID id);

    // GAP-B-26: Find workflow by subject type and subject ID
    Optional<ApprovalWorkflow> findBySubjectTypeAndSubjectId(String subjectType, UUID subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT w FROM ApprovalWorkflow w
        WHERE w.subjectType = :subjectType AND w.subjectId = :subjectId
        """)
    Optional<ApprovalWorkflow> findBySubjectTypeAndSubjectIdForUpdate(
        @Param("subjectType") String subjectType,
        @Param("subjectId") UUID subjectId);

    List<ApprovalWorkflow> findByStatus(WorkflowStatus status);
}
