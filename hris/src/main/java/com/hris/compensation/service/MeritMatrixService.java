package com.hris.compensation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixCellDto;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixCellUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixUpdateDto;
import com.hris.compensation.entity.MeritMatrixCell;
import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.repository.MeritMatrixRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The per-tenant merit matrix (rating band x compa-ratio band -> suggested %),
 * plus the banding helpers used to place an employee into a matrix cell.
 */
@Service
@RequiredArgsConstructor
public class MeritMatrixService {

    private final MeritMatrixRepository matrixRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<MeritMatrixCellDto> getMatrix() {
        return matrixRepository.findAllByOrderByRatingBandAscCompaBandAsc().stream()
            .map(c -> new MeritMatrixCellDto(c.getId(), c.getRatingBand(), c.getCompaBand(), c.getSuggestedPercent()))
            .toList();
    }

    /** Upserts each supplied cell (one row per rating/compa band pair). */
    @Transactional
    public List<MeritMatrixCellDto> updateMatrix(MeritMatrixUpdateDto dto, UUID actorId) {
        for (MeritMatrixCellUpdateDto cell : dto.cells()) {
            MeritMatrixCell entity = matrixRepository
                .findByRatingBandAndCompaBand(cell.ratingBand(), cell.compaBand())
                .orElseGet(() -> MeritMatrixCell.builder()
                    .ratingBand(cell.ratingBand())
                    .compaBand(cell.compaBand())
                    .build());
            entity.setSuggestedPercent(cell.suggestedPercent());
            matrixRepository.save(entity);
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_merit_matrix", null, null, dto);
        return getMatrix();
    }

    /** Suggested % for a cell; 0 when the cell is not configured. */
    @Transactional(readOnly = true)
    public BigDecimal suggestedPercent(RatingBand ratingBand, CompaBand compaBand) {
        return matrixRepository.findByRatingBandAndCompaBand(ratingBand, compaBand)
            .map(MeritMatrixCell::getSuggestedPercent)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Bands a performance rating: {@code <= lowMax -> LOW}, {@code >= highMin -> HIGH},
     * else SOLID. A missing rating (no completed fact) defaults to SOLID (neutral).
     */
    public static RatingBand bandRating(Integer ratingValue, int lowMax, int highMin) {
        if (ratingValue == null) {
            return RatingBand.SOLID;
        }
        if (ratingValue <= lowMax) {
            return RatingBand.LOW;
        }
        if (ratingValue >= highMin) {
            return RatingBand.HIGH;
        }
        return RatingBand.SOLID;
    }

    /**
     * Bands a compa-ratio: {@code < lowMax -> BELOW}, {@code > highMin -> ABOVE},
     * else WITHIN. A null compa-ratio (no grade) defaults to WITHIN (neutral).
     */
    public static CompaBand bandCompa(BigDecimal compaRatio, BigDecimal lowMax, BigDecimal highMin) {
        if (compaRatio == null) {
            return CompaBand.WITHIN;
        }
        if (compaRatio.compareTo(lowMax) < 0) {
            return CompaBand.BELOW;
        }
        if (compaRatio.compareTo(highMin) > 0) {
            return CompaBand.ABOVE;
        }
        return CompaBand.WITHIN;
    }
}
