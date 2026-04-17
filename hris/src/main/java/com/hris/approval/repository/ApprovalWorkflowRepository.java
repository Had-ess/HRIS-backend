package com.hris.approval.repository;

import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    // GAP-B-26: Find workflow by subject type and subject ID
    Optional<ApprovalWorkflow> findBySubjectTypeAndSubjectId(String subjectType, UUID subjectId);

    List<ApprovalWorkflow> findByStatus(WorkflowStatus status);
}
