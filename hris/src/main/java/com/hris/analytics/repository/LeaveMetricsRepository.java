package com.hris.analytics.repository;
import com.hris.analytics.entity.LeaveMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository public interface LeaveMetricsRepository extends JpaRepository<LeaveMetrics, UUID> {
    List<LeaveMetrics> findByPeriodAndDepartmentId(String period, UUID departmentId);
    List<LeaveMetrics> findByPeriod(String period);
}
