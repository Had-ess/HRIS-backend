package com.hris.notification.repository;

import com.hris.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {
    boolean existsByTargetUserId(UUID targetUserId);
    void deleteByTargetUserId(UUID targetUserId);

    @Query("""
        SELECT ne FROM NotificationEvent ne
        WHERE ne.deliveredAt IS NULL
          AND ne.publishedAt < :cutoff
        ORDER BY ne.publishedAt ASC
        """)
    List<NotificationEvent> findUndeliveredBefore(@Param("cutoff") Instant cutoff);
}
