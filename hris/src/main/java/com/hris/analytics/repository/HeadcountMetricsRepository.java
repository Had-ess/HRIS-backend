package com.hris.analytics.repository;
import com.hris.analytics.entity.HeadcountMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@Repository public interface HeadcountMetricsRepository extends JpaRepository<HeadcountMetrics, UUID> {
    List<HeadcountMetrics> findBySnapshotDateAndDepartmentId(LocalDate date, UUID departmentId);
    List<HeadcountMetrics> findBySnapshotDate(LocalDate date);
}
