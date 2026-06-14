package com.hris.compensation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.compensation.dto.CompensationDtos.PayGradeCreateDto;
import com.hris.compensation.dto.CompensationDtos.PayGradeDto;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayGradeService {

    private final PayGradeRepository payGradeRepository;
    private final CompensationRecordRepository recordRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<PayGradeDto> getAll(boolean activeOnly) {
        return (activeOnly
            ? payGradeRepository.findByIsActiveTrueOrderByCodeAsc()
            : payGradeRepository.findAllByOrderByCodeAsc())
            .stream().map(PayGradeService::toDto).toList();
    }

    @Transactional
    public PayGradeDto create(PayGradeCreateDto dto, UUID actorId) {
        String code = dto.code().trim();
        if (payGradeRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A pay grade with this code already exists");
        }
        validateBand(dto);
        PayGrade grade = payGradeRepository.save(PayGrade.builder()
            .code(code)
            .name(dto.name().trim())
            .currency(normalizeCurrency(dto.currency()))
            .payFrequency(dto.payFrequency())
            .minAmount(dto.minAmount())
            .midAmount(dto.midAmount())
            .maxAmount(dto.maxAmount())
            .jobFamily(trimmedOrNull(dto.jobFamily()))
            .isActive(dto.isActive() == null || dto.isActive())
            .build());
        auditLogService.log(actorId, AuditAction.CREATE, "compensation_pay_grade", grade.getId(), null, grade);
        return toDto(grade);
    }

    @Transactional
    public PayGradeDto update(UUID id, PayGradeCreateDto dto, UUID actorId) {
        PayGrade grade = findGrade(id);
        String code = dto.code().trim();
        if (payGradeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new IllegalArgumentException("A pay grade with this code already exists");
        }
        validateBand(dto);
        grade.setCode(code);
        grade.setName(dto.name().trim());
        grade.setCurrency(normalizeCurrency(dto.currency()));
        grade.setPayFrequency(dto.payFrequency());
        grade.setMinAmount(dto.minAmount());
        grade.setMidAmount(dto.midAmount());
        grade.setMaxAmount(dto.maxAmount());
        grade.setJobFamily(trimmedOrNull(dto.jobFamily()));
        if (dto.isActive() != null) {
            grade.setActive(dto.isActive());
        }
        payGradeRepository.save(grade);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_pay_grade", id, null, grade);
        return toDto(grade);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        PayGrade grade = findGrade(id);
        if (recordRepository.existsByPayGradeId(id)) {
            throw new IllegalStateException(
                "Pay grade cannot be deleted because compensation records reference it; deactivate it instead");
        }
        payGradeRepository.delete(grade);
        auditLogService.log(actorId, AuditAction.DELETE, "compensation_pay_grade", id, grade, null);
    }

    private void validateBand(PayGradeCreateDto dto) {
        if (dto.minAmount().compareTo(dto.midAmount()) > 0
            || dto.midAmount().compareTo(dto.maxAmount()) > 0) {
            throw new IllegalArgumentException("Pay grade band must satisfy min <= mid <= max");
        }
    }

    private PayGrade findGrade(UUID id) {
        return payGradeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pay grade not found"));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static PayGradeDto toDto(PayGrade g) {
        return new PayGradeDto(g.getId(), g.getCode(), g.getName(), g.getCurrency(), g.getPayFrequency(),
            g.getMinAmount(), g.getMidAmount(), g.getMaxAmount(), g.getJobFamily(), g.isActive());
    }
}
