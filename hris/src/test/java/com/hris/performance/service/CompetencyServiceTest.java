package com.hris.performance.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.organisation.entity.JobTitle;
import com.hris.organisation.repository.JobTitleRepository;
import com.hris.performance.dto.PerformanceDtos.CompetencyRatingInput;
import com.hris.performance.entity.PerformanceCompetency;
import com.hris.performance.entity.PerformanceCompetencyJobFamily;
import com.hris.performance.entity.PerformanceReviewCompetency;
import com.hris.performance.repository.PerformanceCompetencyJobFamilyRepository;
import com.hris.performance.repository.PerformanceCompetencyRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCompetencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetencyServiceTest {

    @Mock private PerformanceCompetencyRepository competencyRepository;
    @Mock private PerformanceCompetencyJobFamilyRepository familyRepository;
    @Mock private PerformanceReviewCompetencyRepository reviewCompetencyRepository;
    @Mock private PerformanceRatingLevelRepository levelRepository;
    @Mock private JobTitleRepository jobTitleRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CompetencyService service;

    private PerformanceCompetency competency(UUID id, String name, boolean core, boolean active) {
        return PerformanceCompetency.builder().id(id).name(name).isCore(core).isActive(active).build();
    }

    private Employee employee(UUID jobTitleId) {
        return Employee.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).jobTitleId(jobTitleId).build();
    }

    @Test
    @DisplayName("resolveApplicable returns only core competencies when the employee has no matching family")
    void resolveApplicable_coreOnly() {
        UUID coreId = UUID.randomUUID();
        when(competencyRepository.findByIsActiveTrueAndIsCoreTrue())
            .thenReturn(List.of(competency(coreId, "Ownership", true, true)));
        when(jobTitleRepository.findById(any())).thenReturn(Optional.of(
            JobTitle.builder().id(UUID.randomUUID()).family("Engineering").build()));
        when(familyRepository.findByJobFamily("Engineering")).thenReturn(List.of());

        List<PerformanceCompetency> result = service.resolveApplicable(employee(UUID.randomUUID()));

        assertThat(result).extracting(PerformanceCompetency::getName).containsExactly("Ownership");
    }

    @Test
    @DisplayName("resolveApplicable unions core with family-mapped competencies, sorted by name")
    void resolveApplicable_coreUnionFamily() {
        UUID coreId = UUID.randomUUID();
        UUID famId = UUID.randomUUID();
        UUID jobTitleId = UUID.randomUUID();
        when(competencyRepository.findByIsActiveTrueAndIsCoreTrue())
            .thenReturn(List.of(competency(coreId, "Ownership", true, true)));
        when(jobTitleRepository.findById(jobTitleId)).thenReturn(Optional.of(
            JobTitle.builder().id(jobTitleId).family("Engineering").build()));
        when(familyRepository.findByJobFamily("Engineering")).thenReturn(List.of(
            PerformanceCompetencyJobFamily.builder().competencyId(famId).jobFamily("Engineering").build()));
        when(competencyRepository.findAllById(List.of(famId)))
            .thenReturn(List.of(competency(famId, "Code Quality", false, true)));

        List<PerformanceCompetency> result = service.resolveApplicable(employee(jobTitleId));

        assertThat(result).extracting(PerformanceCompetency::getName)
            .containsExactly("Code Quality", "Ownership"); // alphabetical
    }

    @Test
    @DisplayName("resolveApplicable excludes inactive family-mapped competencies")
    void resolveApplicable_excludesInactiveFamily() {
        UUID famId = UUID.randomUUID();
        UUID jobTitleId = UUID.randomUUID();
        when(competencyRepository.findByIsActiveTrueAndIsCoreTrue()).thenReturn(List.of());
        when(jobTitleRepository.findById(jobTitleId)).thenReturn(Optional.of(
            JobTitle.builder().id(jobTitleId).family("Sales").build()));
        when(familyRepository.findByJobFamily("Sales")).thenReturn(List.of(
            PerformanceCompetencyJobFamily.builder().competencyId(famId).jobFamily("Sales").build()));
        when(competencyRepository.findAllById(List.of(famId)))
            .thenReturn(List.of(competency(famId, "Retired", false, false)));

        assertThat(service.resolveApplicable(employee(jobTitleId))).isEmpty();
    }

    @Test
    @DisplayName("snapshotForReview inserts applicable competencies not already on the review")
    void snapshotForReview_idempotent() {
        UUID reviewId = UUID.randomUUID();
        UUID alreadyId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        UUID jobTitleId = UUID.randomUUID();
        when(reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)).thenReturn(List.of(
            PerformanceReviewCompetency.builder().id(UUID.randomUUID()).reviewId(reviewId)
                .competencyId(alreadyId).competencyName("Ownership").build()));
        when(competencyRepository.findByIsActiveTrueAndIsCoreTrue()).thenReturn(List.of(
            competency(alreadyId, "Ownership", true, true),
            competency(freshId, "Adaptability", true, true)));
        when(jobTitleRepository.findById(jobTitleId)).thenReturn(Optional.of(
            JobTitle.builder().id(jobTitleId).family(null).build()));

        service.snapshotForReview(reviewId, employee(jobTitleId));

        ArgumentCaptor<PerformanceReviewCompetency> captor =
            ArgumentCaptor.forClass(PerformanceReviewCompetency.class);
        verify(reviewCompetencyRepository).save(captor.capture());
        assertThat(captor.getValue().getCompetencyId()).isEqualTo(freshId);
        assertThat(captor.getValue().getCompetencyName()).isEqualTo("Adaptability");
    }

    @Test
    @DisplayName("applyCompetencyRatings rejects a line that does not belong to the review")
    void applyCompetencyRatings_ownershipGuard() {
        UUID reviewId = UUID.randomUUID();
        when(reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyCompetencyRatings(reviewId,
            List.of(new CompetencyRatingInput(UUID.randomUUID(), UUID.randomUUID(), "x"))))
            .isInstanceOf(IllegalArgumentException.class);
        verify(reviewCompetencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyCompetencyRatings sets the rating + comment on a line that belongs to the review")
    void applyCompetencyRatings_setsRating() {
        UUID reviewId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        PerformanceReviewCompetency line = PerformanceReviewCompetency.builder()
            .id(lineId).reviewId(reviewId).competencyId(UUID.randomUUID()).competencyName("Ownership").build();
        when(reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)).thenReturn(List.of(line));
        when(levelRepository.findById(levelId)).thenReturn(Optional.of(
            com.hris.performance.entity.PerformanceRatingLevel.builder().id(levelId).numericValue(4).build()));

        service.applyCompetencyRatings(reviewId,
            List.of(new CompetencyRatingInput(lineId, levelId, "Strong")));

        assertThat(line.getRatingLevelId()).isEqualTo(levelId);
        assertThat(line.getComments()).isEqualTo("Strong");
        verify(reviewCompetencyRepository).save(line);
    }

    @Test
    @DisplayName("delete is blocked when the competency is referenced by a review")
    void delete_blockedWhenReferenced() {
        UUID id = UUID.randomUUID();
        when(competencyRepository.findById(id)).thenReturn(Optional.of(competency(id, "Ownership", true, true)));
        when(reviewCompetencyRepository.existsByCompetencyId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
        verify(competencyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete removes the competency and its family mappings when unused")
    void delete_removesWhenUnused() {
        UUID id = UUID.randomUUID();
        PerformanceCompetency c = competency(id, "Ownership", true, true);
        when(competencyRepository.findById(id)).thenReturn(Optional.of(c));
        when(reviewCompetencyRepository.existsByCompetencyId(id)).thenReturn(false);

        service.delete(id, UUID.randomUUID());

        verify(familyRepository).deleteByCompetencyId(id);
        verify(competencyRepository).delete(c);
    }
}
