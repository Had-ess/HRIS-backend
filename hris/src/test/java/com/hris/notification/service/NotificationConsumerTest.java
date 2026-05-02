package com.hris.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.entity.Notification;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
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
@DisplayName("NotificationConsumer Unit Tests")
class NotificationConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageSource messageSource;

    @Test
    @DisplayName("should render admin submitted notification parameters in correct order")
    void shouldRenderAdminSubmittedNotificationParamsInCorrectOrder() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationConsumer notificationConsumer = new NotificationConsumer(
            notificationRepository, userRepository, messageSource, objectMapper);
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("hr@hris.local")
            .firstName("HR")
            .lastName("Admin")
            .localePreference("en")
            .build();

        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.ADMIN_REQUEST_SUBMITTED)
            .targetUserId(userId)
            .titleKey("admin.submitted.title")
            .bodyKey("admin.submitted.body")
            .params(objectMapper.writeValueAsString(Map.of(
                "requesterName", "Ali Ben",
                "trackingNumber", "AR-20260415-00001",
                "requestType", "Salary Certificate"
            )))
            .locale("fr")
            .routingKey("admin.submitted")
            .publishedAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("admin.submitted.title"), any(Object[].class), any(Locale.class)))
            .thenReturn("New administrative request");
        when(messageSource.getMessage(eq("admin.submitted.body"), any(Object[].class), any(Locale.class)))
            .thenAnswer(invocation -> {
                Object[] args = invocation.getArgument(1, Object[].class);
                return args[0] + "|" + args[1] + "|" + args[2];
            });

        notificationConsumer.onAdminEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("New administrative request");
        assertThat(notification.getBody()).isEqualTo("Ali Ben|AR-20260415-00001|Salary Certificate");
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getLinkPath()).isNull();
    }

    @Test
    @DisplayName("should persist project assignment notification link path")
    void shouldPersistProjectAssignmentNotificationLinkPath() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationConsumer notificationConsumer = new NotificationConsumer(
            notificationRepository, userRepository, messageSource, objectMapper);
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("employee@hris.local")
            .firstName("Project")
            .lastName("Member")
            .localePreference("en")
            .build();

        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.PROJECT_ASSIGNED)
            .targetUserId(userId)
            .titleKey("project.assigned.title")
            .bodyKey("project.assigned.body")
            .params(objectMapper.writeValueAsString(Map.of(
                "projectName", "Atlas",
                "targetPath", "/projects/123"
            )))
            .locale("en")
            .routingKey("admin.project.assigned")
            .publishedAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("project.assigned.title"), any(Object[].class), any(Locale.class)))
            .thenReturn("Project assignment");
        when(messageSource.getMessage(eq("project.assigned.body"), any(Object[].class), any(Locale.class)))
            .thenReturn("You have been assigned to Atlas");

        notificationConsumer.onAdminEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getLinkPath()).isEqualTo("/projects/123");
    }

    @Test
    @DisplayName("should render rejected admin request notification parameters in correct order")
    void shouldRenderRejectedAdminRequestNotificationParamsInCorrectOrder() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationConsumer notificationConsumer = new NotificationConsumer(
            notificationRepository, userRepository, messageSource, objectMapper);
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("employee@hris.local")
            .firstName("Admin")
            .lastName("Requester")
            .localePreference("fr")
            .build();

        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.ADMIN_REQUEST_REJECTED)
            .targetUserId(userId)
            .titleKey("admin.rejected.title")
            .bodyKey("admin.rejected.body")
            .params(objectMapper.writeValueAsString(Map.of(
                "trackingNumber", "AR-20260428-00042",
                "rejectionReason", "Missing attachment"
            )))
            .locale("en")
            .routingKey("admin.rejected")
            .publishedAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("admin.rejected.title"), any(Object[].class), any(Locale.class)))
            .thenReturn("Demande refusee");
        when(messageSource.getMessage(eq("admin.rejected.body"), any(Object[].class), any(Locale.class)))
            .thenAnswer(invocation -> {
                Object[] args = invocation.getArgument(1, Object[].class);
                return args[0] + "|" + args[1];
            });

        notificationConsumer.onAdminEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("Demande refusee");
        assertThat(notification.getBody()).isEqualTo("AR-20260428-00042|Missing attachment");
    }

    @Test
    @DisplayName("should fail when target user is missing so the message can reach the DLQ")
    void shouldFailWhenTargetUserMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationConsumer notificationConsumer = new NotificationConsumer(
            notificationRepository, userRepository, messageSource, objectMapper);
        UUID userId = UUID.randomUUID();

        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_SUBMITTED)
            .targetUserId(userId)
            .titleKey("leave.submitted.title")
            .bodyKey("leave.submitted.body")
            .params("[\"Ali Ben\",\"2026-05-01\",\"2026-05-03\",2]")
            .locale("fr")
            .routingKey("leave.submitted")
            .publishedAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationConsumer.onLeaveEvent(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to process notification event LEAVE_SUBMITTED")
            .hasRootCauseMessage("Target user not found for notification event LEAVE_SUBMITTED");

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
