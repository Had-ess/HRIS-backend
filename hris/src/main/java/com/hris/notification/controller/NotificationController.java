package com.hris.notification.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import com.hris.notification.mapper.NotificationMapper;
import com.hris.notification.repository.NotificationRepository;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponseDto>>> getMyNotifications(
            @RequestParam(required = false) Boolean isRead, Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<Notification> page = isRead != null
            ? notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page.map(notificationMapper::toDto))));
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Notification n = notificationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Notification does not belong to current user");
        }
        n.markAsRead();
        notificationRepository.save(n);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        int count = notificationRepository.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(count));
    }
}
