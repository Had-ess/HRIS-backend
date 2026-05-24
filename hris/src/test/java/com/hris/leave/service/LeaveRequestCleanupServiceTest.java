package com.hris.leave.service;

import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveRequestCleanupService Unit Tests")
class LeaveRequestCleanupServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private LeaveRequestCleanupService leaveRequestCleanupService;

    @Test
    @DisplayName("cleanup soft-deletes cancelled requests older than retention")
    void cleanupSoftDeletesExpiredCancelledRequests() {
        LeaveRequest expired = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .status(LeaveStatus.CANCELLED)
            .cancelledAt(Instant.now().minus(Duration.ofHours(30)))
            .build();

        when(leaveRequestRepository.findByStatusAndCancelledAtLessThanEqualAndDeletedAtIsNull(
            eq(LeaveStatus.CANCELLED), any(Instant.class)))
            .thenReturn(List.of(expired));

        int cleaned = leaveRequestCleanupService.cleanupCancelledRequestsOlderThan(Duration.ofHours(24));

        assertThat(cleaned).isEqualTo(1);
        assertThat(expired.getDeletedAt()).isNotNull();
        verify(leaveRequestRepository).saveAll(List.of(expired));
    }

    @Test
    @DisplayName("cleanup does not touch recent cancelled or non-cancelled requests")
    void cleanupSkipsRequestsWhenRepositoryReturnsNoEligibleRows() {
        when(leaveRequestRepository.findByStatusAndCancelledAtLessThanEqualAndDeletedAtIsNull(
            eq(LeaveStatus.CANCELLED), any(Instant.class)))
            .thenReturn(List.of());

        int cleaned = leaveRequestCleanupService.cleanupCancelledRequestsOlderThan(Duration.ofHours(24));

        assertThat(cleaned).isZero();
        verify(leaveRequestRepository, never()).saveAll(any());
    }
}

