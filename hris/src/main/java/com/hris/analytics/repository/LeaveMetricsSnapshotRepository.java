package com.hris.analytics.repository;

import com.hris.analytics.entity.LeaveMetricsSnapshot;
import com.hris.analytics.enums.AnalyticsScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveMetricsSnapshotRepository extends JpaRepository<LeaveMetricsSnapshot, UUID> {
    Optional<LeaveMetricsSnapshot> findBySnapshotDateAndScopeTypeAndScopeId(LocalDate snapshotDate, AnalyticsScopeType scopeType, UUID scopeId);
    List<LeaveMetricsSnapshot> findBySnapshotDateAndScopeType(LocalDate snapshotDate, AnalyticsScopeType scopeType);
    List<LeaveMetricsSnapshot> findBySnapshotDateBetweenAndScopeTypeAndScopeIdOrderBySnapshotDateAsc(
        LocalDate from,
        LocalDate to,
        AnalyticsScopeType scopeType,
        UUID scopeId
    );
    List<LeaveMetricsSnapshot> findBySnapshotDateBetweenAndScopeTypeOrderBySnapshotDateAsc(
        LocalDate from,
        LocalDate to,
        AnalyticsScopeType scopeType
    );
    void deleteBySnapshotDate(LocalDate snapshotDate);
}
