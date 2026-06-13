package com.hris.performance.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.CompetencyCreateDto;
import com.hris.performance.dto.PerformanceDtos.CompetencyDto;
import com.hris.performance.dto.PerformanceDtos.CompetencyRatingInput;
import com.hris.performance.dto.PerformanceDtos.ReviewCompetencyDto;
import com.hris.performance.entity.PerformanceCompetency;
import com.hris.performance.entity.PerformanceCompetencyJobFamily;
import com.hris.performance.entity.PerformanceReviewCompetency;
import com.hris.performance.repository.PerformanceCompetencyJobFamilyRepository;
import com.hris.performance.repository.PerformanceCompetencyRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCompetencyRepository;
import com.hris.organisation.entity.JobTitle;
import com.hris.organisation.repository.JobTitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Competency catalog (per-tenant config) + per-review competency snapshotting and
 * rating. A competency is CORE (applies to everyone) or mapped to job families
 * (matching {@code job_titles.family}). At review generation the applicable set is
 * snapshotted onto the review; the manager rates each on the cycle's rating scale.
 * Ratings are advisory and never feed the goal-weighted computed_score.
 */
@Service
@RequiredArgsConstructor
public class CompetencyService {

    private final PerformanceCompetencyRepository competencyRepository;
    private final PerformanceCompetencyJobFamilyRepository familyRepository;
    private final PerformanceReviewCompetencyRepository reviewCompetencyRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final JobTitleRepository jobTitleRepository;
    private final AuditLogService auditLogService;

    // --- Catalog CRUD ---

    @Transactional(readOnly = true)
    public List<CompetencyDto> getAll() {
        return toDtos(competencyRepository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public List<CompetencyDto> getAllActive() {
        return toDtos(competencyRepository.findByIsActiveTrueOrderByNameAsc());
    }

    /** Distinct job families from the catalog, for the competency-assignment picker. */
    @Transactional(readOnly = true)
    public List<String> getJobFamilies() {
        return jobTitleRepository.findDistinctFamilies();
    }

    @Transactional(readOnly = true)
    public CompetencyDto get(UUID id) {
        PerformanceCompetency competency = findCompetency(id);
        List<String> families = familyRepository.findByCompetencyId(id).stream()
            .map(PerformanceCompetencyJobFamily::getJobFamily).toList();
        return toDto(competency, families);
    }

    @Transactional
    public CompetencyDto create(CompetencyCreateDto dto, UUID actorId) {
        String name = dto.name().trim();
        if (competencyRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A competency with this name already exists");
        }
        boolean core = Boolean.TRUE.equals(dto.isCore());
        PerformanceCompetency competency = competencyRepository.save(PerformanceCompetency.builder()
            .name(name)
            .description(trimToNull(dto.description()))
            .category(dto.category())
            .isCore(core)
            .isActive(dto.isActive() == null || dto.isActive())
            .build());
        replaceFamilies(competency.getId(), core ? List.of() : dto.jobFamilies());
        auditLogService.log(actorId, AuditAction.CREATE, "performance_competency", competency.getId(), null, competency);
        return get(competency.getId());
    }

    @Transactional
    public CompetencyDto update(UUID id, CompetencyCreateDto dto, UUID actorId) {
        PerformanceCompetency competency = findCompetency(id);
        String name = dto.name().trim();
        if (competencyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("A competency with this name already exists");
        }
        boolean core = Boolean.TRUE.equals(dto.isCore());
        competency.setName(name);
        competency.setDescription(trimToNull(dto.description()));
        competency.setCategory(dto.category());
        competency.setCore(core);
        if (dto.isActive() != null) {
            competency.setActive(dto.isActive());
        }
        competencyRepository.save(competency);
        replaceFamilies(id, core ? List.of() : dto.jobFamilies());
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_competency", id, null, competency);
        return get(id);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        PerformanceCompetency competency = findCompetency(id);
        if (reviewCompetencyRepository.existsByCompetencyId(id)) {
            throw new IllegalStateException(
                "Competency cannot be deleted because it is used on a review; deactivate it instead");
        }
        familyRepository.deleteByCompetencyId(id);
        competencyRepository.delete(competency);
        auditLogService.log(actorId, AuditAction.DELETE, "performance_competency", id, competency, null);
    }

    // --- Per-review snapshot + rating ---

    /**
     * Snapshots the employee's applicable competencies (core ∪ family-mapped) onto
     * a freshly generated review. Idempotent: only inserts competencies not already
     * present on the review.
     */
    @Transactional
    public void snapshotForReview(UUID reviewId, Employee employee) {
        Set<UUID> existing = new LinkedHashSet<>();
        for (PerformanceReviewCompetency rc : reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)) {
            existing.add(rc.getCompetencyId());
        }
        List<PerformanceCompetency> applicable = resolveApplicable(employee);
        int order = existing.size();
        for (PerformanceCompetency c : applicable) {
            if (existing.contains(c.getId())) {
                continue;
            }
            reviewCompetencyRepository.save(PerformanceReviewCompetency.builder()
                .reviewId(reviewId)
                .competencyId(c.getId())
                .competencyName(c.getName())
                .category(c.getCategory())
                .displayOrder(order++)
                .build());
        }
    }

    /** Active competencies that apply to this employee: CORE ∪ those mapped to their job family. */
    @Transactional(readOnly = true)
    public List<PerformanceCompetency> resolveApplicable(Employee employee) {
        Map<UUID, PerformanceCompetency> applicable = new LinkedHashMap<>();
        for (PerformanceCompetency core : competencyRepository.findByIsActiveTrueAndIsCoreTrue()) {
            applicable.put(core.getId(), core);
        }
        String family = jobFamilyOf(employee);
        if (family != null && !family.isBlank()) {
            List<UUID> ids = familyRepository.findByJobFamily(family).stream()
                .map(PerformanceCompetencyJobFamily::getCompetencyId).toList();
            for (PerformanceCompetency c : competencyRepository.findAllById(ids)) {
                if (c.isActive()) {
                    applicable.putIfAbsent(c.getId(), c);
                }
            }
        }
        return applicable.values().stream()
            .sorted(Comparator.comparing(PerformanceCompetency::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Persists the manager's per-competency ratings during manager-submit. Each line
     * must belong to the given review (ownership guard). Advisory — does not touch the
     * goal-weighted score.
     */
    @Transactional
    public void applyCompetencyRatings(UUID reviewId, List<CompetencyRatingInput> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return;
        }
        Map<UUID, PerformanceReviewCompetency> byId = new LinkedHashMap<>();
        for (PerformanceReviewCompetency rc : reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)) {
            byId.put(rc.getId(), rc);
        }
        for (CompetencyRatingInput input : ratings) {
            PerformanceReviewCompetency rc = byId.get(input.reviewCompetencyId());
            if (rc == null) {
                throw new IllegalArgumentException("Competency does not belong to this review");
            }
            if (input.ratingLevelId() != null) {
                levelRepository.findById(input.ratingLevelId())
                    .orElseThrow(() -> new EntityNotFoundException("Rating level not found"));
            }
            rc.setRatingLevelId(input.ratingLevelId());
            rc.setComments(trimToNull(input.comments()));
            reviewCompetencyRepository.save(rc);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewCompetencyDto> getReviewCompetencies(UUID reviewId) {
        return reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId).stream()
            .map(rc -> new ReviewCompetencyDto(rc.getId(), rc.getCompetencyId(), rc.getCompetencyName(),
                rc.getCategory(), rc.getRatingLevelId(), rc.getComments(), rc.getDisplayOrder()))
            .toList();
    }

    // --- Helpers ---

    private String jobFamilyOf(Employee employee) {
        if (employee.getJobTitleId() == null) {
            return null;
        }
        return jobTitleRepository.findById(employee.getJobTitleId())
            .map(JobTitle::getFamily).orElse(null);
    }

    private void replaceFamilies(UUID competencyId, List<String> jobFamilies) {
        familyRepository.deleteByCompetencyId(competencyId);
        if (jobFamilies == null) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String family : jobFamilies) {
            if (family != null && !family.isBlank()) {
                distinct.add(family.trim());
            }
        }
        for (String family : distinct) {
            familyRepository.save(PerformanceCompetencyJobFamily.builder()
                .competencyId(competencyId)
                .jobFamily(family)
                .build());
        }
    }

    private List<CompetencyDto> toDtos(List<PerformanceCompetency> competencies) {
        if (competencies.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = competencies.stream().map(PerformanceCompetency::getId).toList();
        Map<UUID, List<String>> familiesByCompetency = new LinkedHashMap<>();
        for (PerformanceCompetencyJobFamily f : familyRepository.findByCompetencyIdIn(ids)) {
            familiesByCompetency.computeIfAbsent(f.getCompetencyId(), k -> new ArrayList<>()).add(f.getJobFamily());
        }
        return competencies.stream()
            .map(c -> toDto(c, familiesByCompetency.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    private CompetencyDto toDto(PerformanceCompetency c, List<String> families) {
        return new CompetencyDto(c.getId(), c.getName(), c.getDescription(), c.getCategory(),
            c.isCore(), c.isActive(), families);
    }

    private PerformanceCompetency findCompetency(UUID id) {
        return competencyRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Competency not found"));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
