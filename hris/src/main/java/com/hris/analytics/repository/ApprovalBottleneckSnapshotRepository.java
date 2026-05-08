package com.hris.analytics.repository;

import com.hris.analytics.entity.ApprovalBottleneckSnapshot;
import com.hris.analytics.enums.AnalyticsScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalBottleneckSnapshotRepository extends JpaRepository<ApprovalBottleneckSnapshot, UUID> {
    List<ApprovalBottleneckSnapshot> findBySnapshotDateAndScopeTypeAndScopeId(LocalDate snapshotDate, AnalyticsScopeType scopeType, UUID scopeId);
    void deleteBySnapshotDate(LocalDate snapshotDate);
}
