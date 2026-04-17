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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    }
}
