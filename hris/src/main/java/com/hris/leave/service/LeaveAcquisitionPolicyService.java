package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyDto;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyMutationDto;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveAcquisitionPolicyService {

    private final LeaveAcquisitionPolicyRepository repository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<LeaveAcquisitionPolicyDto> getAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LeaveAcquisitionPolicyDto getById(UUID id) {
        return toDto(findPolicy(id));
    }

    @Transactional(readOnly = true)
    public List<LeaveAcquisitionPolicyDto> getByLeaveTypeId(UUID leaveTypeId) {
        return repository.findByLeaveTypeIdOrderByCodeAsc(leaveTypeId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LeaveAcquisitionPolicyDto toDtoView(LeaveAcquisitionPolicy policy) {
        return toDto(policy);
    }

    @Transactional(readOnly = true)
    public LeaveAcquisitionPolicy resolveEffectivePolicy(UUID leaveTypeId, LocalDate onDate) {
        return repository.findEffectivePolicy(leaveTypeId, onDate);
    }

    @Transactional
    public LeaveAcquisitionPolicyDto create(LeaveAcquisitionPolicyMutationDto dto, UUID actorId) {
        LeaveType leaveType = resolveTrackedLeaveType(dto.leaveTypeId());
        validate(dto, null);
        LeaveAcquisitionPolicy policy = repository.save(LeaveAcquisitionPolicy.builder()
            .code(dto.code().trim().toUpperCase(Locale.ROOT))
            .name(dto.name().trim())
            .leaveTypeId(leaveType.getId())
            .frequency(dto.frequency())
            .monthlyRate(dto.monthlyRate())
            .annualQuota(dto.annualQuota())
            .dayCap(dto.dayCap())
            .acquisitionDay(dto.acquisitionDay())
            .prorataHire(Boolean.TRUE.equals(dto.prorataHire()))
            .negativeBalanceAllowed(Boolean.TRUE.equals(dto.negativeBalanceAllowed()))
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .active(dto.active())
            .build());
        auditLogService.log(actorId, AuditAction.CREATE, "leave_acquisition_policy", policy.getId(), null, policy);
        return toDto(policy, leaveType);
    }

    @Transactional
    public LeaveAcquisitionPolicyDto update(UUID id, LeaveAcquisitionPolicyMutationDto dto, UUID actorId) {
        LeaveAcquisitionPolicy policy = findPolicy(id);
        LeaveAcquisitionPolicy previous = copy(policy);
        LeaveType leaveType = resolveTrackedLeaveType(dto.leaveTypeId());
        validate(dto, id);
        policy.setCode(dto.code().trim().toUpperCase(Locale.ROOT));
        policy.setName(dto.name().trim());
        policy.setLeaveTypeId(leaveType.getId());
        policy.setFrequency(dto.frequency());
        policy.setMonthlyRate(dto.monthlyRate());
        policy.setAnnualQuota(dto.annualQuota());
        policy.setDayCap(dto.dayCap());
        policy.setAcquisitionDay(dto.acquisitionDay());
        policy.setProrataHire(Boolean.TRUE.equals(dto.prorataHire()));
        policy.setNegativeBalanceAllowed(Boolean.TRUE.equals(dto.negativeBalanceAllowed()));
        policy.setStartDate(dto.startDate());
        policy.setEndDate(dto.endDate());
        policy.setActive(dto.active());
        LeaveAcquisitionPolicy saved = repository.save(policy);
        auditLogService.log(actorId, AuditAction.UPDATE, "leave_acquisition_policy", saved.getId(), previous, saved);
        return toDto(saved, leaveType);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        LeaveAcquisitionPolicy policy = findPolicy(id);
        LeaveAcquisitionPolicy previous = copy(policy);
        policy.setActive(false);
        repository.save(policy);
        auditLogService.log(actorId, AuditAction.UPDATE, "leave_acquisition_policy", policy.getId(), previous, policy);
    }

    private LeaveAcquisitionPolicy findPolicy(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave acquisition policy not found"));
    }

    private LeaveType resolveTrackedLeaveType(UUID leaveTypeId) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        if (!leaveType.isBalanceTracked()) {
            throw new IllegalArgumentException("Acquisition policies require a balance-tracked leave type");
        }
        return leaveType;
    }

    private void validate(LeaveAcquisitionPolicyMutationDto dto, UUID currentId) {
        if (dto.endDate() != null && dto.startDate().isAfter(dto.endDate())) {
            throw new IllegalArgumentException("Policy start date must be on or before end date");
        }
        if (dto.frequency() == AcquisitionFrequency.MONTHLY) {
            if (dto.monthlyRate() == null) {
                throw new IllegalArgumentException("Monthly rate is required for monthly acquisition policies");
            }
            if (dto.acquisitionDay() == null) {
                throw new IllegalArgumentException("Acquisition day is required for monthly acquisition policies");
            }
        }
        if (repository.existsByCode(dto.code().trim().toUpperCase(Locale.ROOT))) {
            if (currentId == null || repository.findById(currentId)
                .map(policy -> !policy.getCode().equalsIgnoreCase(dto.code().trim()))
                .orElse(true)) {
                throw new IllegalArgumentException("Acquisition policy code already exists");
            }
        }
    }

    private LeaveAcquisitionPolicyDto toDto(LeaveAcquisitionPolicy policy) {
        LeaveType leaveType = leaveTypeRepository.findById(policy.getLeaveTypeId()).orElse(null);
        return toDto(policy, leaveType);
    }

    private LeaveAcquisitionPolicyDto toDto(LeaveAcquisitionPolicy policy, LeaveType leaveType) {
        return new LeaveAcquisitionPolicyDto(
            policy.getId(),
            policy.getCode(),
            policy.getName(),
            policy.getLeaveTypeId(),
            leaveType != null ? leaveType.getCode() : null,
            leaveType != null ? leaveType.getName() : null,
            policy.getFrequency(),
            policy.getMonthlyRate(),
            policy.getAnnualQuota(),
            policy.getDayCap(),
            policy.getAcquisitionDay(),
            policy.isProrataHire(),
            policy.isNegativeBalanceAllowed(),
            policy.getStartDate(),
            policy.getEndDate(),
            policy.isActive(),
            policy.getCreatedAt(),
            policy.getUpdatedAt()
        );
    }

    private LeaveAcquisitionPolicy copy(LeaveAcquisitionPolicy policy) {
        return LeaveAcquisitionPolicy.builder()
            .id(policy.getId())
            .code(policy.getCode())
            .name(policy.getName())
            .leaveTypeId(policy.getLeaveTypeId())
            .frequency(policy.getFrequency())
            .monthlyRate(policy.getMonthlyRate())
            .annualQuota(policy.getAnnualQuota())
            .dayCap(policy.getDayCap())
            .acquisitionDay(policy.getAcquisitionDay())
            .prorataHire(policy.isProrataHire())
            .negativeBalanceAllowed(policy.isNegativeBalanceAllowed())
            .startDate(policy.getStartDate())
            .endDate(policy.getEndDate())
            .active(policy.isActive())
            .createdAt(policy.getCreatedAt())
            .updatedAt(policy.getUpdatedAt())
            .build();
    }
}
