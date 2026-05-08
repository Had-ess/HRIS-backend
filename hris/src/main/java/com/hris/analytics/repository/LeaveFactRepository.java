package com.hris.analytics.repository;

import com.hris.analytics.entity.LeaveFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveFactRepository extends JpaRepository<LeaveFact, UUID> {
    Optional<LeaveFact> findByLeaveRequestId(UUID leaveRequestId);
    List<LeaveFact> findByEventDate(LocalDate eventDate);
    List<LeaveFact> findByEventDateBetween(LocalDate from, LocalDate to);
}
