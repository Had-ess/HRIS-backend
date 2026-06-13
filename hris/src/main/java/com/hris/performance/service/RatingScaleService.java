package com.hris.performance.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.RatingLevelDto;
import com.hris.performance.dto.PerformanceDtos.RatingLevelInput;
import com.hris.performance.dto.PerformanceDtos.RatingScaleCreateDto;
import com.hris.performance.dto.PerformanceDtos.RatingScaleDto;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceRatingScale;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceRatingScaleRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingScaleService {

    private final PerformanceRatingScaleRepository scaleRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final PerformanceReviewCycleRepository cycleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RatingScaleDto> getAll() {
        return scaleRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RatingScaleDto> getAllActive() {
        return scaleRepository.findByIsActiveTrueOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RatingScaleDto get(UUID id) {
        return toDto(findScale(id));
    }

    @Transactional
    public RatingScaleDto create(RatingScaleCreateDto dto, UUID actorId) {
        String name = dto.name().trim();
        if (scaleRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A rating scale with this name already exists");
        }
        boolean makeDefault = Boolean.TRUE.equals(dto.isDefault());
        if (makeDefault) {
            clearExistingDefault();
        }
        PerformanceRatingScale scale = scaleRepository.save(PerformanceRatingScale.builder()
            .name(name)
            .isDefault(makeDefault)
            .isActive(dto.isActive() == null || dto.isActive())
            .build());
        saveLevels(scale.getId(), dto.levels());
        auditLogService.log(actorId, AuditAction.CREATE, "performance_rating_scale", scale.getId(), null, scale);
        return toDto(scale);
    }

    @Transactional
    public RatingScaleDto update(UUID id, RatingScaleCreateDto dto, UUID actorId) {
        PerformanceRatingScale scale = findScale(id);
        String name = dto.name().trim();
        if (scaleRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("A rating scale with this name already exists");
        }
        boolean makeDefault = Boolean.TRUE.equals(dto.isDefault());
        if (makeDefault && !scale.isDefault()) {
            clearExistingDefault();
        }
        scale.setName(name);
        scale.setDefault(makeDefault);
        if (dto.isActive() != null) {
            scale.setActive(dto.isActive());
        }
        scaleRepository.save(scale);

        // Levels are frozen once a cycle references the scale (reviews store
        // rating_level_id, so labels must stay stable). Otherwise replace them.
        if (isReferencedByCycle(id)) {
            if (!sameLevelCount(id, dto.levels())) {
                throw new IllegalStateException(
                    "Rating levels cannot be changed because a review cycle already uses this scale");
            }
        } else {
            levelRepository.deleteByScaleId(id);
            saveLevels(id, dto.levels());
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_rating_scale", id, null, scale);
        return toDto(scale);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        PerformanceRatingScale scale = findScale(id);
        if (isReferencedByCycle(id)) {
            throw new IllegalStateException(
                "Rating scale cannot be deleted because a review cycle uses it; deactivate it instead");
        }
        levelRepository.deleteByScaleId(id);
        scaleRepository.delete(scale);
        auditLogService.log(actorId, AuditAction.DELETE, "performance_rating_scale", id, scale, null);
    }

    private void saveLevels(UUID scaleId, List<RatingLevelInput> levels) {
        for (RatingLevelInput level : levels) {
            levelRepository.save(PerformanceRatingLevel.builder()
                .scaleId(scaleId)
                .label(level.label().trim())
                .numericValue(level.numericValue())
                .displayOrder(level.displayOrder())
                .build());
        }
    }

    private void clearExistingDefault() {
        scaleRepository.findFirstByIsDefaultTrue().ifPresent(existing -> {
            existing.setDefault(false);
            scaleRepository.save(existing);
        });
    }

    private boolean isReferencedByCycle(UUID scaleId) {
        return cycleRepository.findAll().stream()
            .anyMatch(c -> scaleId.equals(c.getRatingScaleId()));
    }

    private boolean sameLevelCount(UUID scaleId, List<RatingLevelInput> levels) {
        return levelRepository.findByScaleIdOrderByDisplayOrderAsc(scaleId).size() == levels.size();
    }

    private PerformanceRatingScale findScale(UUID id) {
        return scaleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Rating scale not found"));
    }

    private RatingScaleDto toDto(PerformanceRatingScale scale) {
        List<RatingLevelDto> levels = levelRepository.findByScaleIdOrderByDisplayOrderAsc(scale.getId()).stream()
            .map(l -> new RatingLevelDto(l.getId(), l.getLabel(), l.getNumericValue(), l.getDisplayOrder()))
            .toList();
        return new RatingScaleDto(scale.getId(), scale.getName(), scale.isDefault(), scale.isActive(), levels);
    }
}
