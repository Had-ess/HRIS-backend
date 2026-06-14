package com.hris.performance.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.CalibrationAdjustDto;
import com.hris.performance.dto.PerformanceDtos.CalibrationGridDto;
import com.hris.performance.dto.PerformanceDtos.CalibrationReviewDto;
import com.hris.performance.dto.PerformanceDtos.RatingLevelDto;
import com.hris.performance.entity.PerformanceCalibrationAdjustment;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.repository.PerformanceCalibrationAdjustmentRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 9-box calibration (PERFORMANCE_MODULE_DESIGN.md §2c). Builds the per-cycle grid
 * (performance x potential, each axis bucketed into 3 bands over the cycle's rating scale)
 * and applies audited HR moves. A move remaps only the axis whose band changed to that band's
 * representative level — performance via hr_override_rating_level_id, potential by overwriting
 * potential_rating_level_id — and appends a before/after audit row. Advisory: computed_score
 * (goal-weighted) is never touched; placement is manager + HR only, never shown to the subject.
 */
@Service
@RequiredArgsConstructor
public class CalibrationService {

    private final PerformanceReviewRepository reviewRepository;
    private final PerformanceReviewCycleRepository cycleRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final PerformanceCalibrationAdjustmentRepository adjustmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AccessScopeService accessScopeService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public CalibrationGridDto getGrid(UUID cycleId, UUID actorId) {
        PerformanceReviewCycle cycle = findCycle(cycleId);
        List<PerformanceRatingLevel> levels =
            levelRepository.findByScaleIdOrderByDisplayOrderAsc(cycle.getRatingScaleId());
        Bands bands = new Bands(levels);

        List<CalibrationReviewDto> placed = new ArrayList<>();
        List<CalibrationReviewDto> unplaced = new ArrayList<>();
        for (PerformanceReview review : reviewRepository.findByCycleId(cycleId)) {
            if (!canView(review, actorId)) {
                continue;
            }
            CalibrationReviewDto dto = toDto(review, bands);
            if (dto.performanceBand() != null && dto.potentialBand() != null) {
                placed.add(dto);
            } else {
                unplaced.add(dto);
            }
        }
        placed.sort(Comparator.comparing(CalibrationReviewDto::employeeName, String.CASE_INSENSITIVE_ORDER));
        unplaced.sort(Comparator.comparing(CalibrationReviewDto::employeeName, String.CASE_INSENSITIVE_ORDER));

        return new CalibrationGridDto(cycle.getId(), cycle.getName(), cycle.getStatus(),
            levels.stream().map(CalibrationService::toLevelDto).toList(), placed, unplaced);
    }

    /**
     * Moves a review to the target cell. Only the axis whose band changed is remapped to that
     * band's representative level (performance -> hr_override, potential -> potential level); a
     * before/after audit row is appended. Returns the refreshed grid.
     */
    @Transactional
    public CalibrationGridDto adjust(UUID reviewId, CalibrationAdjustDto dto, UUID actorId) {
        PerformanceReview review = findReview(reviewId);
        if (!canView(review, actorId)) {
            throw new IllegalArgumentException("This review is not in your scope");
        }
        PerformanceReviewCycle cycle = findCycle(review.getCycleId());
        Bands bands = new Bands(levelRepository.findByScaleIdOrderByDisplayOrderAsc(cycle.getRatingScaleId()));

        UUID prevPerfLevelId = effectivePerformanceLevelId(review);
        UUID prevPotLevelId = review.getPotentialRatingLevelId();
        Integer currentPerfBand = bands.bandOf(prevPerfLevelId);
        Integer currentPotBand = bands.bandOf(prevPotLevelId);

        UUID newPerfLevelId = prevPerfLevelId;
        if (!dto.performanceBand().equals(currentPerfBand)) {
            newPerfLevelId = bands.representativeForBand(dto.performanceBand());
            review.setHrOverrideRatingLevelId(newPerfLevelId);
        }
        UUID newPotLevelId = prevPotLevelId;
        if (!dto.potentialBand().equals(currentPotBand)) {
            newPotLevelId = bands.representativeForBand(dto.potentialBand());
            review.setPotentialRatingLevelId(newPotLevelId);
        }
        reviewRepository.save(review);

        adjustmentRepository.save(PerformanceCalibrationAdjustment.builder()
            .reviewId(reviewId)
            .cycleId(review.getCycleId())
            .previousPerformanceLevelId(prevPerfLevelId)
            .newPerformanceLevelId(newPerfLevelId)
            .previousPotentialLevelId(prevPotLevelId)
            .newPotentialLevelId(newPotLevelId)
            .note(trimToNull(dto.note()))
            .adjustedBy(actorId)
            .build());
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_review", reviewId, null, "CALIBRATED");

        return getGrid(review.getCycleId(), actorId);
    }

    // --- Banding ---

    /** Maps rating levels onto 3 bands (1 Low / 2 Mid / 3 High) by normalized numeric value. */
    private static final class Bands {
        private final Map<UUID, PerformanceRatingLevel> byId = new LinkedHashMap<>();
        private final List<PerformanceRatingLevel> levels;
        private final int min;
        private final int max;

        Bands(List<PerformanceRatingLevel> levels) {
            this.levels = levels;
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (PerformanceRatingLevel l : levels) {
                byId.put(l.getId(), l);
                lo = Math.min(lo, l.getNumericValue());
                hi = Math.max(hi, l.getNumericValue());
            }
            this.min = levels.isEmpty() ? 0 : lo;
            this.max = levels.isEmpty() ? 0 : hi;
        }

        PerformanceRatingLevel level(UUID id) {
            return id == null ? null : byId.get(id);
        }

        Integer valueOf(UUID levelId) {
            PerformanceRatingLevel l = level(levelId);
            return l == null ? null : l.getNumericValue();
        }

        String labelOf(UUID levelId) {
            PerformanceRatingLevel l = level(levelId);
            return l == null ? null : l.getLabel();
        }

        Integer bandOf(UUID levelId) {
            PerformanceRatingLevel l = level(levelId);
            return l == null ? null : bandForValue(l.getNumericValue());
        }

        private int bandForValue(int value) {
            if (max == min) {
                return 2;
            }
            double ratio = (value - min) / (double) (max - min);
            if (ratio < 1.0 / 3) {
                return 1;
            }
            return ratio < 2.0 / 3 ? 2 : 3;
        }

        /** The level closest to a band's center ratio ((band-0.5)/3); null if no levels. */
        UUID representativeForBand(int band) {
            if (levels.isEmpty()) {
                return null;
            }
            if (max == min) {
                return levels.get(0).getId();
            }
            double center = (band - 0.5) / 3.0;
            PerformanceRatingLevel best = null;
            double bestDist = Double.MAX_VALUE;
            for (PerformanceRatingLevel l : levels) {
                double ratio = (l.getNumericValue() - min) / (double) (max - min);
                double dist = Math.abs(ratio - center);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = l;
                }
            }
            return best == null ? null : best.getId();
        }
    }

    // --- Helpers ---

    private CalibrationReviewDto toDto(PerformanceReview review, Bands bands) {
        UUID perfLevelId = effectivePerformanceLevelId(review);
        UUID potLevelId = review.getPotentialRatingLevelId();
        Employee employee = employeeRepository.findById(review.getEmployeeId()).orElse(null);
        return new CalibrationReviewDto(
            review.getId(), review.getEmployeeId(),
            employee == null ? "" : displayName(employee),
            review.getJobTitle(), review.getDepartmentId(), review.getStatus(),
            perfLevelId, bands.labelOf(perfLevelId), bands.valueOf(perfLevelId), bands.bandOf(perfLevelId),
            potLevelId, bands.labelOf(potLevelId), bands.valueOf(potLevelId), bands.bandOf(potLevelId),
            adjustmentRepository.existsByReviewId(review.getId()));
    }

    private static UUID effectivePerformanceLevelId(PerformanceReview review) {
        return review.getHrOverrideRatingLevelId() != null
            ? review.getHrOverrideRatingLevelId()
            : review.getOverallRatingLevelId();
    }

    private boolean canView(PerformanceReview review, UUID userId) {
        if (accessScopeService.hasGlobalBusinessRead(userId)) {
            return true;
        }
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(userId);
        return scope.isDepartment() && review.getDepartmentId() != null
            && scope.departmentIds().contains(review.getDepartmentId());
    }

    private static RatingLevelDto toLevelDto(PerformanceRatingLevel l) {
        return new RatingLevelDto(l.getId(), l.getLabel(), l.getNumericValue(), l.getDisplayOrder());
    }

    private PerformanceReview findReview(UUID id) {
        return reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review not found"));
    }

    private PerformanceReviewCycle findCycle(UUID id) {
        return cycleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review cycle not found"));
    }

    private String displayName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(u -> (safe(u.getFirstName()) + " " + safe(u.getLastName())).trim())
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
