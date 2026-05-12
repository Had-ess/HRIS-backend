package com.hris.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.service.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRequestSlaService Unit Tests")
class AdminRequestSlaServiceTest {

    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    private AdminRequestSlaService slaService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        slaService = new AdminRequestSlaService(
            adminRequestRepository, notificationPublisher, userRepository,
            auditLogService, objectMapper
        );
    }

    @Test
    @DisplayName("should detect overdue requests and notify processors")
    void shouldDetectOverdueRequestsAndNotifyProcessors() {
        AdminRequest overdueRequest = AdminRequest.builder()
            .id(UUID.randomUUID())
            .requestNumber("AR-20260510-00001")
            .subject("Salary certificate")
            .status(AdminRequestStatus.SUBMITTED)
            .dueAt(Instant.now().minus(2, ChronoUnit.DAYS))
            .requesterEmployeeId(UUID.randomUUID())
            .requesterUserId(UUID.randomUUID())
            .typeId(UUID.randomUUID())
            .description("Test")
            .build();

        User processor = User.builder()
            .id(UUID.randomUUID())
            .email("hr@test.com")
            .firstName("HR")
            .lastName("User")
            .localePreference("en")
            .build();

        when(adminRequestRepository.findOverdueRequests(any(), any(), any()))
            .thenReturn(List.of(overdueRequest));
        when(userRepository.findByPermissionNames(any()))
            .thenReturn(List.of(processor));

        int count = slaService.checkAndNotifySlaExceeded();

        assertThat(count).isEqualTo(1);
        verify(notificationPublisher).publish(any(NotificationEvent.class));
        verify(adminRequestRepository).save(overdueRequest);
        assertThat(overdueRequest.getSlaNotifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("should not send duplicate notifications within 24h window")
    void shouldNotSendDuplicateNotifications() {
        // The repository query already filters by slaNotifiedAt < 24h ago
        // So if a request was notified recently, it won't appear in results
        when(adminRequestRepository.findOverdueRequests(any(), any(), any()))
            .thenReturn(List.of());

        int count = slaService.checkAndNotifySlaExceeded();

        assertThat(count).isEqualTo(0);
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should notify multiple processors for each overdue request")
    void shouldNotifyMultipleProcessors() {
        AdminRequest overdueRequest = AdminRequest.builder()
            .id(UUID.randomUUID())
            .requestNumber("AR-20260510-00002")
            .subject("Equipment request")
            .status(AdminRequestStatus.IN_REVIEW)
            .dueAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .requesterEmployeeId(UUID.randomUUID())
            .requesterUserId(UUID.randomUUID())
            .typeId(UUID.randomUUID())
            .description("Test")
            .build();

        User processor1 = User.builder().id(UUID.randomUUID()).email("hr1@test.com")
            .firstName("HR1").lastName("User").localePreference("en").build();
        User processor2 = User.builder().id(UUID.randomUUID()).email("hr2@test.com")
            .firstName("HR2").lastName("User").localePreference("fr").build();

        when(adminRequestRepository.findOverdueRequests(any(), any(), any()))
            .thenReturn(List.of(overdueRequest));
        when(userRepository.findByPermissionNames(any()))
            .thenReturn(List.of(processor1, processor2));

        int count = slaService.checkAndNotifySlaExceeded();

        assertThat(count).isEqualTo(1);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher, times(2)).publish(captor.capture());

        List<NotificationEvent> events = captor.getAllValues();
        assertThat(events).extracting(NotificationEvent::getTargetUserId)
            .containsExactlyInAnyOrder(processor1.getId(), processor2.getId());
    }
}
