package com.hris.compensation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanCreateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanDto;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusCycleRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonusPlanServiceTest {

    @Mock private BonusPlanRepository planRepository;
    @Mock private BonusCycleRepository cycleRepository;
    @Mock private BonusAwardRepository awardRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private BonusPlanService service;

    private final UUID actorId = UUID.randomUUID();

    @Test
    @DisplayName("create rejects a duplicate code")
    void create_rejectsDuplicateCode() {
        when(planRepository.existsByCodeIgnoreCase("STI")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
            new BonusPlanCreateDto("STI", "Short-term incentive", new BigDecimal("10.00"), true), actorId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("create saves a plan and returns it")
    void create_savesPlan() {
        when(planRepository.existsByCodeIgnoreCase("STI")).thenReturn(false);
        when(planRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BonusPlanDto dto = service.create(
            new BonusPlanCreateDto("STI", "Short-term incentive", new BigDecimal("10.00"), null), actorId);

        assertThat(dto.code()).isEqualTo("STI");
        assertThat(dto.targetPercent()).isEqualByComparingTo("10.00");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    @DisplayName("delete is blocked while cycles or awards reference the plan")
    void delete_blockedWhenReferenced() {
        UUID id = UUID.randomUUID();
        when(planRepository.findById(id)).thenReturn(Optional.of(BonusPlan.builder().id(id).code("STI").build()));
        when(cycleRepository.existsByBonusPlanId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id, actorId)).isInstanceOf(IllegalStateException.class);
        verify(planRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete removes an unreferenced plan")
    void delete_removesWhenUnreferenced() {
        UUID id = UUID.randomUUID();
        BonusPlan plan = BonusPlan.builder().id(id).code("STI").build();
        when(planRepository.findById(id)).thenReturn(Optional.of(plan));
        when(cycleRepository.existsByBonusPlanId(id)).thenReturn(false);
        when(awardRepository.existsByBonusPlanId(id)).thenReturn(false);

        service.delete(id, actorId);

        verify(planRepository).delete(plan);
    }
}
