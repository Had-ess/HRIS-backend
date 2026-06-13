package com.hris.performance.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.CycleCreateDto;
import com.hris.performance.dto.PerformanceDtos.CycleDto;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.entity.PerformanceReviewCycleDepartment;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceRatingScaleRepository;
import com.hris.performance.repository.PerformanceReviewCycleDepartmentRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Review cycles: CRUD while DRAFT, the DRAFT->ACTIVE->IN_REVIEW->CLOSED status
 * machine, and review generation. Reviewer is resolved from the supervisor spine
 * (escalating to the department head / parent department) and denormalized onto
 * the review row so a later supervisor change cannot reshuffle an in-flight cycle.
 */
@Service
@RequiredArgsConstructor
public class ReviewCycleService {

    private final PerformanceReviewCycleRepository cycleRepository;
    private final PerformanceReviewCycleDepartmentRepository cycleDepartmentRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final PerformanceRatingScaleRepository scaleRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PerformanceReviewService reviewService;
    private final PerformanceNotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CycleDto> getAll() {
        return cycleRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CycleDto get(UUID id) {
        return toDto(findCycle(id));
    }

    @Transactional
    public CycleDto create(CycleCreateDto dto, UUID actorId) {
        scaleRepository.findById(dto.ratingScaleId())
            .orElseThrow(() -> new EntityNotFoundException("Rating scale not found"));
        validateDates(dto);

        PerformanceReviewCycle cycle = cycleRepository.save(PerformanceReviewCycle.builder()
            .name(dto.name().trim())
            .cycleType(dto.cycleType())
            .status(CycleStatus.DRAFT)
            .periodStart(dto.periodStart())
            .periodEnd(dto.periodEnd())
            .selfAssessmentDue(dto.selfAssessmentDue())
            .managerReviewDue(dto.managerReviewDue())
            .opensOn(dto.opensOn())
            .closesOn(dto.closesOn())
            .includeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()))
            .ratingScaleId(dto.ratingScaleId())
            .build());

        replaceDepartments(cycle.getId(), dto.departmentIds());
        auditLogService.log(actorId, AuditAction.CREATE, "performance_review_cycle", cycle.getId(), null, cycle);
        return toDto(cycle);
    }

    @Transactional
    public CycleDto update(UUID id, CycleCreateDto dto, UUID actorId) {
        PerformanceReviewCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft cycles can be edited");
        }
        scaleRepository.findById(dto.ratingScaleId())
            .orElseThrow(() -> new EntityNotFoundException("Rating scale not found"));
        validateDates(dto);

        cycle.setName(dto.name().trim());
        cycle.setCycleType(dto.cycleType());
        cycle.setPeriodStart(dto.periodStart());
        cycle.setPeriodEnd(dto.periodEnd());
        cycle.setSelfAssessmentDue(dto.selfAssessmentDue());
        cycle.setManagerReviewDue(dto.managerReviewDue());
        cycle.setOpensOn(dto.opensOn());
        cycle.setClosesOn(dto.closesOn());
        cycle.setIncludeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()));
        cycle.setRatingScaleId(dto.ratingScaleId());
        cycleRepository.save(cycle);

        replaceDepartments(id, dto.departmentIds());
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_review_cycle", id, null, cycle);
        return toDto(cycle);
    }

    @Transactional
    public CycleDto activate(UUID id, UUID actorId) {
        PerformanceReviewCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft cycles can be activated");
        }
        cycle.setStatus(CycleStatus.ACTIVE);
        if (cycle.getOpensOn() == null) {
            cycle.setOpensOn(LocalDate.now());
        }
        cycleRepository.save(cycle);
        generateReviews(cycle);
        notifyOpened(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_review_cycle", id, null, "ACTIVATED");
        return toDto(cycle);
    }

    @Transactional
    public CycleDto close(UUID id, UUID actorId) {
        PerformanceReviewCycle cycle = findCycle(id);
        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new IllegalStateException("Cycle is already closed");
        }
        cycle.setStatus(CycleStatus.CLOSED);
        cycleRepository.save(cycle);
        for (PerformanceReview review : reviewRepository.findByCycleId(id)) {
            if (review.getStatus() == ReviewStatus.COMPLETED) {
                reviewService.emitFact(review, cycle);
            }
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "performance_review_cycle", id, null, "CLOSED");
        return toDto(cycle);
    }

    /**
     * Creates one review per in-scope ACTIVE employee that does not already have one
     * (idempotent), resolving the reviewer from the spine. Returns the number created.
     */
    @Transactional
    public int generateReviews(PerformanceReviewCycle cycle) {
        Map<UUID, Department> deptById = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<Employee> employees = resolveScope(cycle, deptById);
        int created = 0;
        for (Employee employee : employees) {
            if (reviewRepository.existsByCycleIdAndEmployeeId(cycle.getId(), employee.getId())) {
                continue;
            }
            reviewRepository.save(PerformanceReview.builder()
                .cycleId(cycle.getId())
                .employeeId(employee.getId())
                .reviewerEmployeeId(resolveReviewer(employee, deptById))
                .departmentId(employee.getDepartmentId())
                .jobTitle(employee.getJobTitle())
                .status(ReviewStatus.SELF_ASSESSMENT)
                .build());
            created++;
        }
        return created;
    }

    /** Locks self-assessment: ACTIVE -> IN_REVIEW, pushing open self-assessments to manager review. */
    @Transactional
    public void advanceToInReview(PerformanceReviewCycle cycle) {
        cycle.setStatus(CycleStatus.IN_REVIEW);
        cycleRepository.save(cycle);
        for (PerformanceReview review : reviewRepository.findByCycleIdAndStatusIn(
                cycle.getId(), List.of(ReviewStatus.SELF_ASSESSMENT))) {
            review.setStatus(ReviewStatus.MANAGER_REVIEW);
            reviewRepository.save(review);
        }
    }

    UUID resolveReviewer(Employee employee, Map<UUID, Department> deptById) {
        if (employee.getSupervisorEmployeeId() != null) {
            return employee.getSupervisorEmployeeId();
        }
        // No direct supervisor: escalate to the department head, walking up parents.
        UUID deptId = employee.getDepartmentId();
        int guard = 0;
        while (deptId != null && guard++ < 50) {
            Department dept = deptById.get(deptId);
            if (dept == null) {
                break;
            }
            if (dept.getHeadEmployeeId() != null && !dept.getHeadEmployeeId().equals(employee.getId())) {
                return dept.getHeadEmployeeId();
            }
            deptId = dept.getParentDepartmentId();
        }
        return null; // HR finalizes reviews with no resolvable reviewer
    }

    private List<Employee> resolveScope(PerformanceReviewCycle cycle, Map<UUID, Department> deptById) {
        List<PerformanceReviewCycleDepartment> scope = cycleDepartmentRepository.findByCycleId(cycle.getId());
        if (scope.isEmpty()) {
            return employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        }
        Set<UUID> deptIds = new LinkedHashSet<>();
        for (PerformanceReviewCycleDepartment row : scope) {
            deptIds.add(row.getDepartmentId());
        }
        if (cycle.isIncludeSubDepartments()) {
            deptIds.addAll(descendantsOf(deptIds, deptById));
        }
        return employeeRepository.findByDepartmentIdInAndStatus(new ArrayList<>(deptIds), EmployeeStatus.ACTIVE);
    }

    private Set<UUID> descendantsOf(Set<UUID> roots, Map<UUID, Department> deptById) {
        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            UUID parent = queue.poll();
            for (Department d : deptById.values()) {
                if (parent.equals(d.getParentDepartmentId()) && result.add(d.getId())) {
                    queue.add(d.getId());
                }
            }
        }
        return result;
    }

    private void notifyOpened(PerformanceReviewCycle cycle) {
        for (PerformanceReview review : reviewRepository.findByCycleId(cycle.getId())) {
            employeeRepository.findById(review.getEmployeeId()).ifPresent(employee ->
                notificationService.notifyCycleOpened(employee, cycle.getName(),
                    cycle.getSelfAssessmentDue() != null ? cycle.getSelfAssessmentDue().toString() : ""));
        }
    }

    private void replaceDepartments(UUID cycleId, List<UUID> departmentIds) {
        cycleDepartmentRepository.deleteByCycleId(cycleId);
        if (departmentIds == null) {
            return;
        }
        for (UUID departmentId : new LinkedHashSet<>(departmentIds)) {
            cycleDepartmentRepository.save(PerformanceReviewCycleDepartment.builder()
                .cycleId(cycleId)
                .departmentId(departmentId)
                .build());
        }
    }

    private void validateDates(CycleCreateDto dto) {
        if (dto.periodEnd().isBefore(dto.periodStart())) {
            throw new IllegalArgumentException("Period end cannot be before period start");
        }
        if (dto.opensOn() != null && dto.closesOn() != null && dto.closesOn().isBefore(dto.opensOn())) {
            throw new IllegalArgumentException("Closes-on cannot be before opens-on");
        }
    }

    private PerformanceReviewCycle findCycle(UUID id) {
        return cycleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review cycle not found"));
    }

    private CycleDto toDto(PerformanceReviewCycle cycle) {
        List<UUID> departmentIds = cycleDepartmentRepository.findByCycleId(cycle.getId()).stream()
            .map(PerformanceReviewCycleDepartment::getDepartmentId)
            .toList();
        return new CycleDto(
            cycle.getId(), cycle.getName(), cycle.getCycleType(), cycle.getStatus(),
            cycle.getPeriodStart(), cycle.getPeriodEnd(), cycle.getSelfAssessmentDue(),
            cycle.getManagerReviewDue(), cycle.getOpensOn(), cycle.getClosesOn(),
            cycle.isIncludeSubDepartments(), cycle.getRatingScaleId(), departmentIds,
            reviewRepository.countByCycleId(cycle.getId()),
            reviewRepository.countByCycleIdAndStatus(cycle.getId(), ReviewStatus.COMPLETED));
    }
}
