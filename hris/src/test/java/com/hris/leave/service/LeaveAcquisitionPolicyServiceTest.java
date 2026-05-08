package com.hris.leave.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyMutationDto;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveAcquisitionPolicyService Unit Tests")
class LeaveAcquisitionPolicyServiceTest {

    @Mock private LeaveAcquisitionPolicyRepository repository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private LeaveAcquisitionPolicyService service;

    @Test
    @DisplayName("create rejects non balance tracked leave type")
    void createRejectsNonBalanceTrackedLeaveType() {
        UUID leaveTypeId = UUID.randomUUID();
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(LeaveType.builder()
            .id(leaveTypeId)
            .code("SICK")
            .name("Sick")
            .balanceTracked(false)
            .build()));

        assertThatThrownBy(() -> service.create(baseDto(leaveTypeId), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Acquisition policies require a balance-tracked leave type");
    }

    @Test
    @DisplayName("create rejects monthly policy without rate")
    void createRejectsMissingMonthlyRate() {
        UUID leaveTypeId = UUID.randomUUID();
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(LeaveType.builder()
            .id(leaveTypeId)
            .code("ANNUAL")
            .name("Annual")
            .balanceTracked(true)
            .build()));

        var dto = new LeaveAcquisitionPolicyMutationDto(
            "ANNUAL_MONTHLY",
            "Annual monthly",
            leaveTypeId,
            AcquisitionFrequency.MONTHLY,
            null,
            24,
            30,
            25,
            true,
            false,
            LocalDate.of(2026, 1, 1),
            null,
            true
        );

        assertThatThrownBy(() -> service.create(dto, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Monthly rate is required for monthly acquisition policies");
    }

    @Test
    @DisplayName("create normalizes policy code and preserves balance flags")
    void createNormalizesPolicyCodeAndPreservesFlags() {
        UUID leaveTypeId = UUID.randomUUID();
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(LeaveType.builder()
            .id(leaveTypeId)
            .code("ANNUAL")
            .name("Annual")
            .balanceTracked(true)
            .build()));
        when(repository.existsByCode("ANNUAL_MONTHLY")).thenReturn(false);
        when(repository.save(any(LeaveAcquisitionPolicy.class))).thenAnswer(invocation -> {
            LeaveAcquisitionPolicy saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.create(baseDto(leaveTypeId), UUID.randomUUID());

        assertThat(result.code()).isEqualTo("ANNUAL_MONTHLY");
        assertThat(result.prorataHire()).isTrue();
        assertThat(result.negativeBalanceAllowed()).isFalse();
    }

    private LeaveAcquisitionPolicyMutationDto baseDto(UUID leaveTypeId) {
        return new LeaveAcquisitionPolicyMutationDto(
            "ANNUAL_MONTHLY",
            "Annual monthly",
            leaveTypeId,
            AcquisitionFrequency.MONTHLY,
            2,
            24,
            30,
            25,
            true,
            false,
            LocalDate.of(2026, 1, 1),
            null,
            true
        );
    }
}
