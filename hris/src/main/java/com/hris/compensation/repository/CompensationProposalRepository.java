package com.hris.compensation.repository;

import com.hris.compensation.entity.CompensationProposal;
import com.hris.compensation.enums.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompensationProposalRepository extends JpaRepository<CompensationProposal, UUID> {

    List<CompensationProposal> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);

    List<CompensationProposal> findByCycleIdAndManagerEmployeeIdOrderByCreatedAtAsc(
        UUID cycleId, UUID managerEmployeeId);

    List<CompensationProposal> findByCycleIdAndDepartmentId(UUID cycleId, UUID departmentId);

    List<CompensationProposal> findByCycleIdAndStatus(UUID cycleId, ProposalStatus status);

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, ProposalStatus status);

    @Query("SELECT COALESCE(SUM(p.proposedIncreaseAmount), 0) FROM CompensationProposal p "
        + "WHERE p.cycleId = :cycleId AND p.status = :status")
    java.math.BigDecimal sumProposedIncreaseByCycleIdAndStatus(
        @Param("cycleId") UUID cycleId, @Param("status") ProposalStatus status);

    @Query("SELECT DISTINCT p.cycleId FROM CompensationProposal p WHERE p.managerEmployeeId = :managerEmployeeId")
    List<UUID> findDistinctCycleIdsByManagerEmployeeId(@Param("managerEmployeeId") UUID managerEmployeeId);

    @Query("SELECT DISTINCT p.departmentId FROM CompensationProposal p "
        + "WHERE p.cycleId = :cycleId AND p.managerEmployeeId = :managerEmployeeId")
    List<UUID> findDistinctDepartmentIdsByCycleIdAndManagerEmployeeId(
        @Param("cycleId") UUID cycleId, @Param("managerEmployeeId") UUID managerEmployeeId);
}
