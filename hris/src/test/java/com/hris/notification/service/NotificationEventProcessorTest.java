package com.hris.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.entity.Notification;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.mapper.NotificationMapper;
import com.hris.notification.repository.NotificationEventRepository;
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
@DisplayName("NotificationEventProcessor Unit Tests")
class NotificationEventProcessorTest {

    private static ObjectMapper testObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationStreamService notificationStreamService;

    private NotificationEventProcessor newProcessor(ObjectMapper objectMapper) {
        return new NotificationEventProcessor(
            notificationRepository, notificationEventRepository, userRepository,
            messageSource, objectMapper, notificationMapper, notificationStreamService);
    }

    @Test
    @DisplayName("should render admin submitted notification parameters in correct order")
    void shouldRenderAdminSubmittedNotificationParamsInCorrectOrder() throws Exception {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
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
                "requestNumber", "AR-20260415-00001",
                "subject", "Salary certificate",
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
                return args[0] + "|" + args[1];
            });
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("New administrative request");
        assertThat(notification.getBody()).isEqualTo("AR-20260415-00001|Salary Certificate");
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getLinkPath()).isNull();
    }

    @Test
    @DisplayName("should persist project assignment notification link path")
    void shouldPersistProjectAssignmentNotificationLinkPath() throws Exception {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
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
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getLinkPath()).isEqualTo("/projects/123");
    }

    @Test
    @DisplayName("should render rejected admin request notification parameters in correct order")
    void shouldRenderRejectedAdminRequestNotificationParamsInCorrectOrder() throws Exception {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
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
                "requestNumber", "AR-20260428-00042",
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
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("Demande refusee");
        assertThat(notification.getBody()).isEqualTo("AR-20260428-00042|Missing attachment");
    }

    @Test
    @DisplayName("should fail when target user is missing so the outbox worker can retry")
    void shouldFailWhenTargetUserMissing() {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
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

        assertThatThrownBy(() -> processor.process(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to process notification event LEAVE_SUBMITTED")
            .hasRootCauseMessage("Target user not found for notification event LEAVE_SUBMITTED");

        verify(notificationRepository, never()).save(any(Notification.class));
        assertThat(event.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("should settle duplicate notification event without creating a second notification")
    void shouldSettleDuplicateNotificationEvent() {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationEvent event = NotificationEvent.builder()
            .id(eventId)
            .eventType(NotificationEventType.LEAVE_SUBMITTED)
            .targetUserId(userId)
            .titleKey("leave.submitted.title")
            .bodyKey("leave.submitted.body")
            .params("{\"employeeName\":\"Test\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-03\",\"workingDays\":2}")
            .locale("en")
            .routingKey("leave.submitted")
            .publishedAt(Instant.now())
            .build();

        when(notificationRepository.existsByEventId(eventId)).thenReturn(true);

        processor.process(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        // The duplicate row is settled so the outbox worker stops retrying it.
        assertThat(event.getDeliveredAt()).isNotNull();
        verify(notificationEventRepository).save(event);
    }

    @Test
    @DisplayName("should process LEAVE_CANCELLED notification event and stamp deliveredAt")
    void shouldProcessLeaveCancelledEvent() throws Exception {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("supervisor@hris.local")
            .firstName("Super")
            .lastName("Visor")
            .localePreference("en")
            .build();

        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_CANCELLED)
            .targetUserId(userId)
            .titleKey("leave.cancelled.title")
            .bodyKey("leave.cancelled.body")
            .params(objectMapper.writeValueAsString(Map.of(
                "employeeName", "John Doe",
                "startDate", "2026-06-01",
                "endDate", "2026-06-05",
                "workingDays", 5
            )))
            .locale("en")
            .routingKey("leave.cancelled")
            .publishedAt(Instant.now())
            .build();

        when(notificationRepository.existsByEventId(any())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("leave.cancelled.title"), any(Object[].class), any(Locale.class)))
            .thenReturn("Leave request cancelled");
        when(messageSource.getMessage(eq("leave.cancelled.body"), any(Object[].class), any(Locale.class)))
            .thenReturn("John Doe cancelled their leave");
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("Leave request cancelled");
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getEventId()).isNotNull();
        assertThat(event.getDeliveredAt()).isNotNull();
        verify(notificationEventRepository).save(event);
    }

    @Test
    @DisplayName("should push the saved notification to the SSE stream")
    void shouldPushNotificationToStream() throws Exception {
        ObjectMapper objectMapper = testObjectMapper();
        NotificationEventProcessor processor = newProcessor(objectMapper);
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
            .eventType(NotificationEventType.LEAVE_ACCRUAL_APPLIED)
            .targetUserId(userId)
            .titleKey("leave.accrual.summary.title")
            .bodyKey("leave.accrual.summary.body")
            .params(objectMapper.writeValueAsString(Map.of(
                "policiesProcessed", 3,
                "transactionsCreated", 15,
                "runDate", "2026-05-10"
            )))
            .locale("en")
            .routingKey("system.accrual.summary")
            .publishedAt(Instant.now())
            .build();

        when(notificationRepository.existsByEventId(any())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(messageSource.getMessage(eq("leave.accrual.summary.title"), any(Object[].class), any(Locale.class)))
            .thenReturn("Accrual run completed");
        when(messageSource.getMessage(eq("leave.accrual.summary.body"), any(Object[].class), any(Locale.class)))
            .thenReturn("3 policies processed, 15 transactions");
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event);

        // No transaction in unit tests, so the push happens inline.
        verify(notificationStreamService).push(eq(userId), any());
    }
}
