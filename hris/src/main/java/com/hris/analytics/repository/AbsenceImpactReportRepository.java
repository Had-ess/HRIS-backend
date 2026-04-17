package com.hris.analytics.repository;
import com.hris.analytics.entity.AbsenceImpactReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository public interface AbsenceImpactReportRepository extends JpaRepository<AbsenceImpactReport, UUID> {
    List<AbsenceImpactReport> findByProjectId(UUID projectId);
}
