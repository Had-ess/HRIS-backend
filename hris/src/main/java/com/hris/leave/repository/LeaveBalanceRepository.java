package com.hris.leave.repository;

import com.hris.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);
    List<LeaveBalance> findByEmployeeIdAndYear(UUID employeeId, int year);
    boolean existsByEmployeeId(UUID employeeId);
    void deleteByEmployeeId(UUID employeeId);
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.year = :year")
    List<LeaveBalance> findAllByYear(@Param("year") int year);
}
