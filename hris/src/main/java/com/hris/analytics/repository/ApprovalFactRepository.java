package com.hris.analytics.repository;

import com.hris.analytics.entity.ApprovalFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalFactRepository extends JpaRepository<ApprovalFact, UUID> {
    Optional<ApprovalFact> findByApprovalStepId(UUID approvalStepId);
    List<ApprovalFact> findByEventDate(LocalDate eventDate);
}
