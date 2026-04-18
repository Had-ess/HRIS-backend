package com.hris.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.notification.service.NotificationPublisher;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRequestService Unit Tests")
class AdminRequestServiceTest {

    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private AdminRequestTypeRepository adminRequestTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AdminRequestService adminRequestService;

    private UUID requesterId;
    private UUID requestTypeId;
    private User requesterUser;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        requestTypeId = UUID.randomUUID();
        requesterUser = User.builder()
            .id(requesterId)
            .firstName("Ali")
            .lastName("Ben")
            .build();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create admin request with SUBMITTED status and tracking number")
        void shouldCreateAdminRequest_Successfully() throws Exception {
            CreateAdminRequestDto dto = new CreateAdminRequestDto(
                requestTypeId, "Need salary certificate", UrgencyLevel.NORMAL, null
            );

            AdminRequestType type = new AdminRequestType();
            type.setId(requestTypeId);
            type.setName("Salary Certificate");

            when(adminRequestRepository.save(any(AdminRequest.class)))
                .thenAnswer(inv -> {
                    AdminRequest r = inv.getArgument(0);
                    r.setId(UUID.randomUUID());
                    return r;
                });
            when(userRepository.findById(requesterId)).thenReturn(Optional.of(requesterUser));
            when(adminRequestTypeRepository.findById(requestTypeId)).thenReturn(Optional.of(type));
            when(userRepository.findByRole("HR_ADMIN")).thenReturn(List.of(requesterUser));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // Act
            AdminRequest result = adminRequestService.create(dto, requesterId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AdminRequestStatus.SUBMITTED);
            assertThat(result.getTrackingNumber()).startsWith("AR-");
            assertThat(result.getRequesterId()).isEqualTo(requesterId);
            assertThat(result.getDescription()).isEqualTo("Need salary certificate");

            verify(adminRequestRepository).save(any(AdminRequest.class));
            verify(auditLogService).log(eq(requesterId), any(), eq("admin_request"), any(), any(), any());
            verify(notificationPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("process()")
    class ProcessTests {

        @Test
        @DisplayName("should process a submitted request")
        void shouldProcessRequest_Successfully() throws JsonProcessingException {
            UUID requestId = UUID.randomUUID();
            UUID hrAdminId = UUID.randomUUID();

            AdminRequest request = AdminRequest.builder()
                .id(requestId)
                .requesterId(requesterId)
                .trackingNumber("AR-20260411-00001")
                .status(AdminRequestStatus.SUBMITTED)
                .build();

            User hrAdmin = User.builder().id(hrAdminId).build();

            when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            when(userRepository.findById(requesterId)).thenReturn(Optional.of(requesterUser));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // Act
            adminRequestService.process(requestId, hrAdminId);

            // Assert
            assertThat(request.getStatus()).isEqualTo(AdminRequestStatus.PROCESSED);
            assertThat(request.getResolvedById()).isEqualTo(hrAdminId);
            assertThat(request.getResolvedAt()).isNotNull();

            verify(adminRequestRepository).save(request);
            verify(notificationPublisher).publish(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException when request already processed")
        void shouldThrow_WhenAlreadyProcessed() {
            UUID requestId = UUID.randomUUID();
            UUID hrAdminId = UUID.randomUUID();

            AdminRequest request = AdminRequest.builder()
                .id(requestId)
                .status(AdminRequestStatus.PROCESSED)
                .build();

            when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> adminRequestService.process(requestId, hrAdminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot process an admin request in status: PROCESSED");
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when request not found")
        void shouldThrow_WhenNotFound() {
            UUID requestId = UUID.randomUUID();
            when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminRequestService.process(requestId, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("reject()")
    class RejectTests {

        @Test
        @DisplayName("should reject an admin request")
        void shouldRejectRequest() {
            UUID requestId = UUID.randomUUID();
            UUID hrAdminId = UUID.randomUUID();

            AdminRequest request = AdminRequest.builder()
                .id(requestId)
                .status(AdminRequestStatus.SUBMITTED)
                .build();

            when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            // Act
            adminRequestService.reject(requestId, hrAdminId);

            // Assert
            assertThat(request.getStatus()).isEqualTo(AdminRequestStatus.REJECTED);
            assertThat(request.getResolvedById()).isEqualTo(hrAdminId);
            assertThat(request.getResolvedAt()).isNotNull();

            verify(adminRequestRepository).save(request);
            verify(auditLogService).log(eq(hrAdminId), any(), eq("admin_request"), eq(requestId), any(), any());
        }

        @Test
        @DisplayName("should throw when rejecting a processed request")
        void shouldThrow_WhenRejectingProcessedRequest() {
            UUID requestId = UUID.randomUUID();

            AdminRequest request = AdminRequest.builder()
                .id(requestId)
                .status(AdminRequestStatus.PROCESSED)
                .build();

            when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> adminRequestService.reject(requestId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot reject an admin request in status: PROCESSED");
        }
    }

    @Test
    @DisplayName("should not process a rejected request")
    void shouldNotProcessRejectedRequest() {
        UUID requestId = UUID.randomUUID();
        UUID hrAdminId = UUID.randomUUID();

        AdminRequest request = AdminRequest.builder()
            .id(requestId)
            .status(AdminRequestStatus.REJECTED)
            .build();

        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminRequestService.process(requestId, hrAdminId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot process an admin request in status: REJECTED");
    }

    @Test
    @DisplayName("should not reject a processed request")
    void shouldNotRejectProcessedRequest() {
        UUID requestId = UUID.randomUUID();
        UUID hrAdminId = UUID.randomUUID();

        AdminRequest request = AdminRequest.builder()
            .id(requestId)
            .status(AdminRequestStatus.PROCESSED)
            .build();

        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminRequestService.reject(requestId, hrAdminId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot reject an admin request in status: PROCESSED");
    }

    @Test
    @DisplayName("should not process a cancelled request")
    void shouldNotProcessCancelledRequest() {
        UUID requestId = UUID.randomUUID();
        UUID hrAdminId = UUID.randomUUID();

        AdminRequest request = AdminRequest.builder()
            .id(requestId)
            .status(AdminRequestStatus.CANCELLED)
            .build();

        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminRequestService.process(requestId, hrAdminId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot process an admin request in status: CANCELLED");
    }
}
