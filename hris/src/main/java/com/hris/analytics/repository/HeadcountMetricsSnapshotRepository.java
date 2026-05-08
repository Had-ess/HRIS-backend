package com.hris.analytics.repository;

import com.hris.analytics.entity.HeadcountMetricsSnapshot;
import com.hris.analytics.enums.AnalyticsScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HeadcountMetricsSnapshotRepository extends JpaRepository<HeadcountMetricsSnapshot, UUID> {
    Optional<HeadcountMetricsSnapshot> findBySnapshotDateAndScopeTypeAndScopeId(LocalDate snapshotDate, AnalyticsScopeType scopeType, UUID scopeId);
    List<HeadcountMetricsSnapshot> findBySnapshotDateBetweenAndScopeTypeAndScopeIdOrderBySnapshotDateAsc(
        LocalDate from,
        LocalDate to,
        AnalyticsScopeType scopeType,
        UUID scopeId
    );
    List<HeadcountMetricsSnapshot> findBySnapshotDateBetweenAndScopeTypeOrderBySnapshotDateAsc(
        LocalDate from,
        LocalDate to,
        AnalyticsScopeType scopeType
    );
    void deleteBySnapshotDate(LocalDate snapshotDate);
}
