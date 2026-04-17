package com.hris.leave.repository;

import com.hris.auth.enums.ContractType;
import com.hris.leave.entity.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {
    @Query("""
        SELECT lp FROM LeavePolicy lp
        WHERE lp.leaveTypeId = :leaveTypeId
          AND lp.contractType = :contractType
          AND lp.minSeniorityYears <= :seniorityYears
        ORDER BY lp.minSeniorityYears DESC
        LIMIT 1
        """)
    Optional<LeavePolicy> findApplicablePolicy(
        @Param("leaveTypeId") UUID leaveTypeId,
        @Param("contractType") ContractType contractType,
        @Param("seniorityYears") int seniorityYears);
}
