package com.hris.compensation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.compensation.dto.CompensationDtos.PayGradeCreateDto;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayGradeServiceTest {

    @Mock private PayGradeRepository payGradeRepository;
    @Mock private CompensationRecordRepository recordRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PayGradeService service;

    private final UUID actorId = UUID.randomUUID();

    private PayGradeCreateDto dto(String code, long min, long mid, long max) {
        return new PayGradeCreateDto(code, "Grade", "USD", PayFrequency.ANNUAL,
            BigDecimal.valueOf(min), BigDecimal.valueOf(mid), BigDecimal.valueOf(max), null, true);
    }

    @Test
    @DisplayName("create rejects a duplicate code")
    void create_rejectsDuplicateCode() {
        when(payGradeRepository.existsByCodeIgnoreCase("G1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(dto("G1", 1, 2, 3), actorId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(payGradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects a band where min > mid")
    void create_rejectsBadBand() {
        when(payGradeRepository.existsByCodeIgnoreCase(any())).thenReturn(false);
        assertThatThrownBy(() -> service.create(dto("G1", 100, 50, 200), actorId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(payGradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create persists a valid grade")
    void create_persists() {
        when(payGradeRepository.existsByCodeIgnoreCase(any())).thenReturn(false);
        when(payGradeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create(dto("G2", 40000, 50000, 60000), actorId);
        verify(payGradeRepository).save(any());
    }

    @Test
    @DisplayName("delete is blocked while a compensation record references the grade")
    void delete_blockedWhenInUse() {
        UUID id = UUID.randomUUID();
        when(payGradeRepository.findById(id)).thenReturn(Optional.of(PayGrade.builder().id(id).build()));
        when(recordRepository.existsByPayGradeId(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(id, actorId))
            .isInstanceOf(IllegalStateException.class);
        verify(payGradeRepository, never()).delete(any());
    }
}
