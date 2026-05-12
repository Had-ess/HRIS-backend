package com.hris.leave.acquisition.repository;

import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveAcquisitionPolicyRepository extends JpaRepository<LeaveAcquisitionPolicy, UUID> {

    List<LeaveAcquisitionPolicy> findAllByOrderByCodeAsc();

    List<LeaveAcquisitionPolicy> findByLeaveTypeIdOrderByCodeAsc(UUID leaveTypeId);

    List<LeaveAcquisitionPolicy> findByActiveTrueAndFrequencyOrderByCodeAsc(AcquisitionFrequency frequency);

    List<LeaveAcquisitionPolicy> findByLeaveTypeIdAndActiveTrueOrderByStartDateDesc(UUID leaveTypeId);

    boolean existsByCode(String code);

    default LeaveAcquisitionPolicy findEffectivePolicy(UUID leaveTypeId, LocalDate onDate) {
        return findByLeaveTypeIdAndActiveTrueOrderByStartDateDesc(leaveTypeId).stream()
            .filter(policy -> policy.isEffectiveOn(onDate))
            .findFirst()
            .orElse(null);
    }
}
