package com.hris.notification.controller;

import com.hris.common.ApiResponse;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import com.hris.notification.mapper.NotificationMapper;
import com.hris.notification.repository.NotificationRepository;
import com.hris.support.TestAuthenticationFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController Unit Tests")
class NotificationControllerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Test
    @DisplayName("unread count only returns the current user's count")
    void unreadCountOnlyReturnsTheCurrentUsersCount() {
        UUID userId = UUID.randomUUID();
        NotificationController controller = new NotificationController(notificationRepository, notificationMapper);
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(4L);

        ResponseEntity<ApiResponse<Long>> response = controller.getUnreadCount(
            TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE")
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(4L);
        verify(notificationRepository).countByUserIdAndIsReadFalse(userId);
    }

    @Test
    @DisplayName("mark as read only updates a notification owned by the current user")
    void markAsReadOnlyUpdatesNotificationOwnedByCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
            .id(notificationId)
            .userId(userId)
            .title("Title")
            .body("Body")
            .isRead(false)
            .createdAt(Instant.now())
            .build();
        NotificationController controller = new NotificationController(notificationRepository, notificationMapper);

        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));

        ResponseEntity<ApiResponse<Void>> response = controller.markAsRead(
            notificationId,
            TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE")
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).findByIdAndUserId(notificationId, userId);
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("mark as read rejects notifications owned by another user")
    void markAsReadRejectsNotificationsOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationController controller = new NotificationController(notificationRepository, notificationMapper);

        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.markAsRead(
            notificationId,
            TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE")
        ))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Notification not found");

        verify(notificationRepository).findByIdAndUserId(notificationId, userId);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("get notifications preserves wrapped page response shape")
    void getNotificationsPreservesWrappedPageResponseShape() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
            .id(notificationId)
            .userId(userId)
            .title("Title")
            .body("Body")
            .isRead(false)
            .createdAt(Instant.now())
            .build();
        NotificationResponseDto dto = new NotificationResponseDto(
            notificationId,
            userId,
            "Title",
            "Body",
            "/projects/123",
            false,
            notification.getCreatedAt()
        );
        NotificationController controller = new NotificationController(notificationRepository, notificationMapper);

        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(eq(userId), eq(false), any()))
            .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));
        when(notificationMapper.toDto(notification)).thenReturn(dto);

        ResponseEntity<ApiResponse<com.hris.common.PageResponse<NotificationResponseDto>>> response =
            controller.getMyNotifications(
                false,
                PageRequest.of(0, 20),
                TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE")
            );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).containsExactly(dto);
        assertThat(response.getBody().data().totalElements()).isEqualTo(1);
    }
}
