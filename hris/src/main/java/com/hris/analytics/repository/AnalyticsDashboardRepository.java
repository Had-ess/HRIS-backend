package com.hris.analytics.repository;
import com.hris.analytics.entity.AnalyticsDashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository public interface AnalyticsDashboardRepository extends JpaRepository<AnalyticsDashboard, UUID> {}
