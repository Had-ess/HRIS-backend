package com.hris.leave.accrual.repository;

import com.hris.leave.accrual.entity.LeaveAccrualRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LeaveAccrualRunRepository extends JpaRepository<LeaveAccrualRun, UUID> {

    Page<LeaveAccrualRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
