package com.hris.compensation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordCreateDto;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class CompensationServiceTest {

    @Mock private CompensationRecordRepository recordRepository;
    @Mock private PayGradeRepository payGradeRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeService employeeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CompensationService service;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID gradeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private PayGrade grade(PayFrequency freq, long mid) {
        return PayGrade.builder()
            .id(gradeId).code("G3").name("Professional").currency("USD")
            .payFrequency(freq)
            .minAmount(BigDecimal.valueOf(mid - 20000))
            .midAmount(BigDecimal.valueOf(mid))
            .maxAmount(BigDecimal.valueOf(mid + 20000))
            .isActive(true)
            .build();
    }

    private CompensationRecordCreateDto dto(BigDecimal base, PayFrequency freq, UUID payGradeId, LocalDate eff) {
        return new CompensationRecordCreateDto(payGradeId, base, "USD", freq, eff,
            CompensationChangeReason.MERIT, "raise");
    }

    @Test
    @DisplayName("addRecord computes compa-ratio = annualized base / grade midpoint")
    void addRecord_computesCompaRatio() {
        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(payGradeRepository.findById(gradeId)).thenReturn(Optional.of(grade(PayFrequency.ANNUAL, 100000)));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.empty());
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompensationRecordDto result = service.addRecord(employeeId,
            dto(BigDecimal.valueOf(110000), PayFrequency.ANNUAL, gradeId, LocalDate.of(2026, 1, 1)), actorId);

        assertThat(result.compaRatio()).isEqualByComparingTo("1.1000");
        assertThat(result.payGradeCode()).isEqualTo("G3");
    }

    @Test
    @DisplayName("addRecord annualizes an hourly base before dividing by an annual midpoint")
    void addRecord_annualizesHourly() {
        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(payGradeRepository.findById(gradeId)).thenReturn(Optional.of(grade(PayFrequency.ANNUAL, 104000)));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.empty());
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // $50/hr * 2080 = $104,000 annual == midpoint -> compa 1.0000
        CompensationRecordDto result = service.addRecord(employeeId,
            dto(BigDecimal.valueOf(50), PayFrequency.HOURLY, gradeId, LocalDate.of(2026, 1, 1)), actorId);

        assertThat(result.compaRatio()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("addRecord leaves compa-ratio null when no grade is chosen")
    void addRecord_noGradeNullCompa() {
        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.empty());
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompensationRecordDto result = service.addRecord(employeeId,
            dto(BigDecimal.valueOf(90000), PayFrequency.ANNUAL, null, LocalDate.of(2026, 1, 1)), actorId);

        assertThat(result.compaRatio()).isNull();
    }

    @Test
    @DisplayName("addRecord supersedes the existing current record before inserting the new one")
    void addRecord_supersedesCurrent() {
        CompensationRecord current = CompensationRecord.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).baseAmount(BigDecimal.valueOf(90000))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).effectiveDate(LocalDate.of(2025, 1, 1))
            .isCurrent(true).changeReason(CompensationChangeReason.HIRE).build();
        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.of(current));
        when(recordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addRecord(employeeId,
            dto(BigDecimal.valueOf(100000), PayFrequency.ANNUAL, null, LocalDate.of(2026, 1, 1)), actorId);

        ArgumentCaptor<CompensationRecord> flushed = ArgumentCaptor.forClass(CompensationRecord.class);
        verify(recordRepository).saveAndFlush(flushed.capture());
        assertThat(flushed.getValue().isCurrent()).isFalse();
        assertThat(flushed.getValue().getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    @DisplayName("addRecord rejects an effective date before the current record")
    void addRecord_rejectsBackdate() {
        CompensationRecord current = CompensationRecord.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).baseAmount(BigDecimal.valueOf(90000))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).effectiveDate(LocalDate.of(2026, 6, 1))
            .isCurrent(true).changeReason(CompensationChangeReason.HIRE).build();
        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.addRecord(employeeId,
            dto(BigDecimal.valueOf(100000), PayFrequency.ANNUAL, null, LocalDate.of(2026, 1, 1)), actorId))
            .isInstanceOf(IllegalArgumentException.class);

        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMyCompensation resolves the employee from the current user")
    void getMyCompensation_resolvesEmployee() {
        UUID userId = UUID.randomUUID();
        Employee employee = new Employee();
        employee.setId(employeeId);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.empty());
        when(recordRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId))
            .thenReturn(java.util.List.of());

        var result = service.getMyCompensation(userId);

        assertThat(result.current()).isNull();
        assertThat(result.history()).isEmpty();
    }
}
