package com.hris.leave.repository;

import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    Page<LeaveRequest> findByEmployeeId(UUID employeeId, Pageable pageable);
    Page<LeaveRequest> findByEmployeeIdAndStatus(UUID employeeId, LeaveStatus status, Pageable pageable);
    List<LeaveRequest> findByEmployeeId(UUID employeeId);
}
