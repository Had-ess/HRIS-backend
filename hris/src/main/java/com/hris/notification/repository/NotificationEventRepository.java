package com.hris.notification.repository;

import com.hris.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {
    boolean existsByTargetUserId(UUID targetUserId);
    void deleteByTargetUserId(UUID targetUserId);
}
