package com.hris.compensation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixCellUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixUpdateDto;
import com.hris.compensation.entity.MeritMatrixCell;
import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.repository.MeritMatrixRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeritMatrixServiceTest {

    @Mock private MeritMatrixRepository matrixRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private MeritMatrixService service;

    @Test
    @DisplayName("bandRating: <= lowMax -> LOW, >= highMin -> HIGH, between -> SOLID, null -> SOLID")
    void bandsRating() {
        assertThat(MeritMatrixService.bandRating(2, 2, 4)).isEqualTo(RatingBand.LOW);
        assertThat(MeritMatrixService.bandRating(4, 2, 4)).isEqualTo(RatingBand.HIGH);
        assertThat(MeritMatrixService.bandRating(3, 2, 4)).isEqualTo(RatingBand.SOLID);
        assertThat(MeritMatrixService.bandRating(null, 2, 4)).isEqualTo(RatingBand.SOLID);
    }

    @Test
    @DisplayName("bandCompa: < lowMax -> BELOW, > highMin -> ABOVE, between -> WITHIN, null -> WITHIN")
    void bandsCompa() {
        BigDecimal low = new BigDecimal("0.90");
        BigDecimal high = new BigDecimal("1.10");
        assertThat(MeritMatrixService.bandCompa(new BigDecimal("0.85"), low, high)).isEqualTo(CompaBand.BELOW);
        assertThat(MeritMatrixService.bandCompa(new BigDecimal("1.20"), low, high)).isEqualTo(CompaBand.ABOVE);
        assertThat(MeritMatrixService.bandCompa(new BigDecimal("1.00"), low, high)).isEqualTo(CompaBand.WITHIN);
        assertThat(MeritMatrixService.bandCompa(null, low, high)).isEqualTo(CompaBand.WITHIN);
    }

    @Test
    @DisplayName("suggestedPercent returns the cell value, or zero when the cell is missing")
    void looksUpSuggestedPercent() {
        when(matrixRepository.findByRatingBandAndCompaBand(RatingBand.HIGH, CompaBand.BELOW))
            .thenReturn(Optional.of(MeritMatrixCell.builder()
                .ratingBand(RatingBand.HIGH).compaBand(CompaBand.BELOW)
                .suggestedPercent(new BigDecimal("6.00")).build()));
        when(matrixRepository.findByRatingBandAndCompaBand(RatingBand.LOW, CompaBand.ABOVE))
            .thenReturn(Optional.empty());

        assertThat(service.suggestedPercent(RatingBand.HIGH, CompaBand.BELOW)).isEqualByComparingTo("6.00");
        assertThat(service.suggestedPercent(RatingBand.LOW, CompaBand.ABOVE)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("updateMatrix upserts each supplied cell")
    void upsertsCells() {
        when(matrixRepository.findByRatingBandAndCompaBand(any(), any())).thenReturn(Optional.empty());
        when(matrixRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(matrixRepository.findAllByOrderByRatingBandAscCompaBandAsc()).thenReturn(List.of());

        service.updateMatrix(new MeritMatrixUpdateDto(List.of(
            new MeritMatrixCellUpdateDto(RatingBand.HIGH, CompaBand.WITHIN, new BigDecimal("5.0")))), UUID.randomUUID());

        verify(matrixRepository).save(any(MeritMatrixCell.class));
    }
}
