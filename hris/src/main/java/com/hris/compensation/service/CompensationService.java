package com.hris.compensation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordCreateDto;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationDtos.MyCompensationDto;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompensationService {

    // Hours assumed worked per year when annualizing an hourly rate (40h * 52w).
    private static final BigDecimal ANNUAL_HOURS = BigDecimal.valueOf(2080);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final CompensationRecordRepository recordRepository;
    private final PayGradeRepository payGradeRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;

    /** Employee self-view: own current record + full history. */
    @Transactional(readOnly = true)
    public MyCompensationDto getMyCompensation(UUID currentUserId) {
        Employee employee = employeeRepository.findByUserId(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("No employee profile for the current user"));
        return new MyCompensationDto(
            currentDto(employee.getId()),
            history(employee.getId()));
    }

    /** HR view of an employee's compensation history. Scope enforced by EmployeeService.getById. */
    @Transactional(readOnly = true)
    public List<CompensationRecordDto> listRecords(UUID employeeId, UUID requesterId) {
        employeeService.getById(employeeId, requesterId);
        return history(employeeId);
    }

    @Transactional(readOnly = true)
    public CompensationRecordDto getCurrent(UUID employeeId, UUID requesterId) {
        employeeService.getById(employeeId, requesterId);
        return currentDto(employeeId);
    }

    /**
     * Records a pay change. Any existing current record is superseded (closed the
     * day before the new effective date), then the new current record is inserted
     * with its compa-ratio against the chosen grade.
     */
    @Transactional
    public CompensationRecordDto addRecord(UUID employeeId, CompensationRecordCreateDto dto, UUID actorId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EntityNotFoundException("Employee not found");
        }
        if (dto.baseAmount().signum() <= 0) {
            throw new IllegalArgumentException("Base amount must be greater than zero");
        }

        PayGrade grade = null;
        if (dto.payGradeId() != null) {
            grade = payGradeRepository.findById(dto.payGradeId())
                .orElseThrow(() -> new EntityNotFoundException("Pay grade not found"));
        }

        recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
            .ifPresent(current -> supersede(current, dto.effectiveDate()));

        BigDecimal compaRatio = computeCompaRatio(dto.baseAmount(), dto.payFrequency(), grade);
        CompensationRecord created = recordRepository.save(CompensationRecord.builder()
            .employeeId(employeeId)
            .payGradeId(grade == null ? null : grade.getId())
            .baseAmount(dto.baseAmount())
            .currency(dto.currency().trim().toUpperCase())
            .payFrequency(dto.payFrequency())
            .effectiveDate(dto.effectiveDate())
            .isCurrent(true)
            .changeReason(dto.changeReason())
            .compaRatio(compaRatio)
            .note(trimmedOrNull(dto.note()))
            .createdBy(actorId)
            .build());

        auditLogService.log(actorId, AuditAction.CREATE, "compensation_record", created.getId(), null, created);
        return toDto(created, grade);
    }

    private void supersede(CompensationRecord current, java.time.LocalDate newEffective) {
        if (newEffective.isBefore(current.getEffectiveDate())) {
            throw new IllegalArgumentException(
                "Effective date cannot be before the current compensation record's effective date");
        }
        current.setCurrent(false);
        java.time.LocalDate closedEnd = newEffective.minusDays(1);
        current.setEndDate(closedEnd.isBefore(current.getEffectiveDate())
            ? current.getEffectiveDate()
            : closedEnd);
        // Flush the UPDATE before inserting the new current row: Hibernate orders
        // INSERTs before UPDATEs within a flush, which would trip the one-current
        // partial unique index (uq_comp_records_current).
        recordRepository.saveAndFlush(current);
    }

    /** compa_ratio = annualized base / annualized grade midpoint (4 dp, HALF_UP). */
    BigDecimal computeCompaRatio(BigDecimal base, PayFrequency baseFreq, PayGrade grade) {
        if (grade == null) {
            return null;
        }
        BigDecimal mid = annualize(grade.getMidAmount(), grade.getPayFrequency());
        if (mid == null || mid.signum() <= 0) {
            return null;
        }
        BigDecimal annualBase = annualize(base, baseFreq);
        return annualBase.divide(mid, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal annualize(BigDecimal amount, PayFrequency freq) {
        if (amount == null || freq == null) {
            return amount;
        }
        return switch (freq) {
            case ANNUAL -> amount;
            case MONTHLY -> amount.multiply(MONTHS_PER_YEAR);
            case HOURLY -> amount.multiply(ANNUAL_HOURS);
        };
    }

    private CompensationRecordDto currentDto(UUID employeeId) {
        return recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
            .map(r -> toDto(r, gradeOrNull(r.getPayGradeId())))
            .orElse(null);
    }

    private List<CompensationRecordDto> history(UUID employeeId) {
        List<CompensationRecord> records =
            recordRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId);
        Map<UUID, PayGrade> gradeCache = new HashMap<>();
        return records.stream()
            .map(r -> toDto(r, r.getPayGradeId() == null ? null
                : gradeCache.computeIfAbsent(r.getPayGradeId(), this::gradeOrNull)))
            .toList();
    }

    private PayGrade gradeOrNull(UUID gradeId) {
        return gradeId == null ? null : payGradeRepository.findById(gradeId).orElse(null);
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static CompensationRecordDto toDto(CompensationRecord r, PayGrade grade) {
        return new CompensationRecordDto(
            r.getId(),
            r.getEmployeeId(),
            r.getPayGradeId(),
            grade == null ? null : grade.getCode(),
            grade == null ? null : grade.getName(),
            r.getBaseAmount(),
            r.getCurrency(),
            r.getPayFrequency(),
            r.getEffectiveDate(),
            r.getEndDate(),
            r.isCurrent(),
            r.getChangeReason(),
            r.getCompaRatio(),
            r.getNote(),
            r.getCreatedAt()
        );
    }
}
