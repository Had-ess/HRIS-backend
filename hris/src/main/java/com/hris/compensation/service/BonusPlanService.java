package com.hris.compensation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanCreateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanDto;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusCycleRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BonusPlanService {

    private final BonusPlanRepository planRepository;
    private final BonusCycleRepository cycleRepository;
    private final BonusAwardRepository awardRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<BonusPlanDto> getAll(boolean activeOnly) {
        return (activeOnly
            ? planRepository.findByIsActiveTrueOrderByCodeAsc()
            : planRepository.findAllByOrderByCodeAsc())
            .stream().map(BonusPlanService::toDto).toList();
    }

    @Transactional
    public BonusPlanDto create(BonusPlanCreateDto dto, UUID actorId) {
        String code = dto.code().trim();
        if (planRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A bonus plan with this code already exists");
        }
        BonusPlan plan = planRepository.save(BonusPlan.builder()
            .code(code)
            .name(dto.name().trim())
            .targetPercent(dto.targetPercent())
            .isActive(dto.isActive() == null || dto.isActive())
            .build());
        auditLogService.log(actorId, AuditAction.CREATE, "compensation_bonus_plan", plan.getId(), null, plan);
        return toDto(plan);
    }

    @Transactional
    public BonusPlanDto update(UUID id, BonusPlanCreateDto dto, UUID actorId) {
        BonusPlan plan = findPlan(id);
        String code = dto.code().trim();
        if (planRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new IllegalArgumentException("A bonus plan with this code already exists");
        }
        plan.setCode(code);
        plan.setName(dto.name().trim());
        plan.setTargetPercent(dto.targetPercent());
        if (dto.isActive() != null) {
            plan.setActive(dto.isActive());
        }
        planRepository.save(plan);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_plan", id, null, plan);
        return toDto(plan);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        BonusPlan plan = findPlan(id);
        if (cycleRepository.existsByBonusPlanId(id) || awardRepository.existsByBonusPlanId(id)) {
            throw new IllegalStateException(
                "Bonus plan cannot be deleted because cycles or awards reference it; deactivate it instead");
        }
        planRepository.delete(plan);
        auditLogService.log(actorId, AuditAction.DELETE, "compensation_bonus_plan", id, plan, null);
    }

    BonusPlan findPlan(UUID id) {
        return planRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Bonus plan not found"));
    }

    static BonusPlanDto toDto(BonusPlan p) {
        return new BonusPlanDto(p.getId(), p.getCode(), p.getName(), p.getTargetPercent(), p.isActive());
    }
}
