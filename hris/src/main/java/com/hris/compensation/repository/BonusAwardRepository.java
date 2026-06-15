package com.hris.compensation.repository;

import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.enums.BonusAwardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface BonusAwardRepository extends JpaRepository<BonusAward, UUID> {

    List<BonusAward> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);

    List<BonusAward> findByCycleIdAndManagerEmployeeIdOrderByCreatedAtAsc(UUID cycleId, UUID managerEmployeeId);

    List<BonusAward> findByCycleIdAndDepartmentId(UUID cycleId, UUID departmentId);

    List<BonusAward> findByCycleIdAndStatus(UUID cycleId, BonusAwardStatus status);

    List<BonusAward> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<BonusAward> findByEmployeeIdAndStatus(UUID employeeId, BonusAwardStatus status);

    List<BonusAward> findByStatus(BonusAwardStatus status);

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    boolean existsByBonusPlanId(UUID bonusPlanId);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, BonusAwardStatus status);

    @Query("SELECT COALESCE(SUM(a.awardedAmount), 0) FROM BonusAward a "
        + "WHERE a.cycleId = :cycleId AND a.status = :status")
    BigDecimal sumAwardedByCycleIdAndStatus(@Param("cycleId") UUID cycleId, @Param("status") BonusAwardStatus status);

    @Query("SELECT DISTINCT a.cycleId FROM BonusAward a "
        + "WHERE a.managerEmployeeId = :managerEmployeeId AND a.cycleId IS NOT NULL")
    List<UUID> findDistinctCycleIdsByManagerEmployeeId(@Param("managerEmployeeId") UUID managerEmployeeId);

    @Query("SELECT DISTINCT a.departmentId FROM BonusAward a "
        + "WHERE a.cycleId = :cycleId AND a.managerEmployeeId = :managerEmployeeId")
    List<UUID> findDistinctDepartmentIdsByCycleIdAndManagerEmployeeId(
        @Param("cycleId") UUID cycleId, @Param("managerEmployeeId") UUID managerEmployeeId);
}
