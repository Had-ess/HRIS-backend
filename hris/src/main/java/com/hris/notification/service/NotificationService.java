package com.hris.notification.service;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import com.hris.notification.mapper.NotificationMapper;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyNotifications(UUID userId, Boolean isRead, Pageable pageable) {
        Page<Notification> page = isRead != null
            ? notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.markAsRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId);
    }
}
