package com.hris.analytics.repository;

import com.hris.analytics.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    @Query("""
        select ae from AnalyticsEvent ae
        where ae.processedAt is null
        order by ae.occurredAt asc, ae.id asc
        """)
    List<AnalyticsEvent> findPending(org.springframework.data.domain.Pageable pageable);

    @Query("""
        select count(ae) from AnalyticsEvent ae
        where ae.processedAt is null
        """)
    long countPending();
}
