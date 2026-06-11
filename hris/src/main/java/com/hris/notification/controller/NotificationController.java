package com.hris.notification.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.enums.NotificationType;
import com.hris.notification.service.NotificationService;
import com.hris.notification.service.NotificationStreamService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    /**
     * Server-Sent Events stream for real-time notification delivery.
     * EventSource cannot set an Authorization header, so this endpoint also accepts the
     * access token as an {@code access_token} query parameter (see SecurityConfig).
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return notificationStreamService.subscribe(userId);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponseDto>>> getMyNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationType type,
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            notificationService.getMyNotifications(userId, isRead, type, pageable))));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markAllAsRead(userId)));
    }
}
