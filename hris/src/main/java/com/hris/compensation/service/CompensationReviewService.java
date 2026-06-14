package com.hris.compensation.service;

import com.hris.analytics.entity.PerformanceFact;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.PerformanceFactRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordCreateDto;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationReviewDtos.BudgetPoolDto;
import com.hris.compensation.dto.CompensationReviewDtos.BudgetPoolUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ReviewCycleCreateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ReviewCycleDto;
import com.hris.compensation.entity.CompensationBudgetPool;
import com.hris.compensation.entity.CompensationProposal;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.CompensationReviewCycle;
import com.hris.compensation.entity.CompensationReviewCycleDepartment;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.ProposalStatus;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import com.hris.compensation.repository.CompensationBudgetPoolRepository;
import com.hris.compensation.repository.CompensationProposalRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.CompensationReviewCycleDepartmentRepository;
import com.hris.compensation.repository.CompensationReviewCycleRepository;
import com.hris.compensation.repository.PayGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Merit / compensation-review cycles. Drives the perform -> calibrate -> reward
 * loop: generates per-employee proposals seeded from performance facts + current
 * compa-ratio + the merit matrix, draws them down against per-department budget
 * pools, routes through a single HR approval gate, and on apply writes new
 * compensation records via the Phase-1 supersede.
 */
@Service
@RequiredArgsConstructor
public class CompensationReviewService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CompensationReviewCycleRepository cycleRepository;
    private final CompensationReviewCycleDepartmentRepository cycleDepartmentRepository;
    private final CompensationBudgetPoolRepository poolRepository;
    private final CompensationProposalRepository proposalRepository;
    private final CompensationRecordRepository recordRepository;
    private final PayGradeRepository payGradeRepository;
    private final PerformanceFactRepository performanceFactRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final MeritMatrixService meritMatrixService;
    private final CompensationService compensationService;
    private final AuditLogService auditLogService;

    // --- Cycles (HR) ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReviewCycleDto> getAll() {
        return cycleRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toCycleDto).toList();
    }

    @Transactional(readOnly = true)
    public ReviewCycleDto get(UUID id) {
        return toCycleDto(findCycle(id));
    }

    @Transactional
    public ReviewCycleDto create(ReviewCycleCreateDto dto, UUID actorId) {
        validateConfig(dto);
        CompensationReviewCycle cycle = cycleRepository.save(CompensationReviewCycle.builder()
            .name(dto.name().trim())
            .status(ReviewCycleStatus.DRAFT)
            .sourcePerformanceCycleId(dto.sourcePerformanceCycleId())
            .effectiveDate(dto.effectiveDate())
            .defaultBudgetPercent(dto.defaultBudgetPercent())
            .ratingLowMax(dto.ratingLowMax())
            .ratingHighMin(dto.ratingHighMin())
            .compaLowMax(dto.compaLowMax())
            .compaHighMin(dto.compaHighMin())
            .includeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()))
            .createdBy(actorId)
            .build());
        replaceDepartments(cycle.getId(), dto.departmentIds());
        auditLogService.log(actorId, AuditAction.CREATE, "compensation_review_cycle", cycle.getId(), null, cycle);
        return toCycleDto(cycle);
    }

    @Transactional
    public ReviewCycleDto update(UUID id, ReviewCycleCreateDto dto, UUID actorId) {
        CompensationReviewCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.DRAFT, "Only draft cycles can be edited");
        validateConfig(dto);
        cycle.setName(dto.name().trim());
        cycle.setSourcePerformanceCycleId(dto.sourcePerformanceCycleId());
        cycle.setEffectiveDate(dto.effectiveDate());
        cycle.setDefaultBudgetPercent(dto.defaultBudgetPercent());
        cycle.setRatingLowMax(dto.ratingLowMax());
        cycle.setRatingHighMin(dto.ratingHighMin());
        cycle.setCompaLowMax(dto.compaLowMax());
        cycle.setCompaHighMin(dto.compaHighMin());
        cycle.setIncludeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()));
        cycleRepository.save(cycle);
        replaceDepartments(id, dto.departmentIds());
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_review_cycle", id, null, cycle);
        return toCycleDto(cycle);
    }

    /** DRAFT -> ACTIVE: snapshot per-department budget pools and generate proposals. */
    @Transactional
    public ReviewCycleDto activate(UUID id, UUID actorId) {
        CompensationReviewCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.DRAFT, "Only draft cycles can be activated");

        Map<UUID, Department> deptById = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<Employee> employees = resolveScope(cycle, deptById);

        Map<UUID, BigDecimal> payrollByDept = new HashMap<>();
        for (Employee employee : employees) {
            if (proposalRepository.existsByCycleIdAndEmployeeId(cycle.getId(), employee.getId())) {
                continue;
            }
            CompensationRecord current = recordRepository
                .findByEmployeeIdAndIsCurrentTrue(employee.getId()).orElse(null);
            if (current == null) {
                continue; // no current pay -> not part of the merit cycle
            }
            CompensationProposal proposal = buildProposal(cycle, employee, current, deptById);
            proposalRepository.save(proposal);
            BigDecimal annualBase = CompensationService.annualize(current.getBaseAmount(), current.getPayFrequency());
            payrollByDept.merge(employee.getDepartmentId(), annualBase, BigDecimal::add);
        }

        if (!poolRepository.existsByCycleId(cycle.getId())) {
            for (Map.Entry<UUID, BigDecimal> entry : payrollByDept.entrySet()) {
                BigDecimal basePayroll = entry.getValue();
                BigDecimal budgetAmount = basePayroll
                    .multiply(cycle.getDefaultBudgetPercent())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                poolRepository.save(CompensationBudgetPool.builder()
                    .cycleId(cycle.getId())
                    .departmentId(entry.getKey())
                    .basePayroll(basePayroll)
                    .budgetPercent(cycle.getDefaultBudgetPercent())
                    .budgetAmount(budgetAmount)
                    .build());
            }
        }

        cycle.setStatus(ReviewCycleStatus.ACTIVE);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_review_cycle", id, null, "ACTIVATED");
        return toCycleDto(cycle);
    }

    /** ACTIVE -> IN_REVIEW: lock manager input for HR approval. */
    @Transactional
    public ReviewCycleDto advanceToReview(UUID id, UUID actorId) {
        CompensationReviewCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.ACTIVE, "Only active cycles can advance to review");
        cycle.setStatus(ReviewCycleStatus.IN_REVIEW);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_review_cycle", id, null, "IN_REVIEW");
        return toCycleDto(cycle);
    }

    /** IN_REVIEW -> CLOSED: apply every APPROVED proposal as a new compensation record. */
    @Transactional
    public ReviewCycleDto applyAndClose(UUID id, UUID actorId) {
        CompensationReviewCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.IN_REVIEW, "Only in-review cycles can be applied");

        for (CompensationProposal proposal : proposalRepository.findByCycleIdAndStatus(id, ProposalStatus.APPROVED)) {
            CompensationRecordDto record = compensationService.addRecord(
                proposal.getEmployeeId(),
                new CompensationRecordCreateDto(
                    proposal.getPayGradeId(),
                    proposal.getProposedBaseAmount(),
                    proposal.getCurrency(),
                    proposal.getPayFrequency(),
                    cycle.getEffectiveDate(),
                    proposal.getChangeReason(),
                    proposal.getNote()),
                actorId);
            proposal.setStatus(ProposalStatus.APPLIED);
            proposal.setAppliedRecordId(record.id());
            proposalRepository.save(proposal);
        }

        cycle.setStatus(ReviewCycleStatus.CLOSED);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_review_cycle", id, null, "CLOSED");
        return toCycleDto(cycle);
    }

    // --- Budget pools (HR) ----------------------------------------------------

    @Transactional(readOnly = true)
    public List<BudgetPoolDto> getPools(UUID cycleId) {
        findCycle(cycleId);
        return poolRepository.findByCycleId(cycleId).stream().map(this::toPoolDto).toList();
    }

    @Transactional
    public BudgetPoolDto updatePool(UUID poolId, BudgetPoolUpdateDto dto, UUID actorId) {
        CompensationBudgetPool pool = poolRepository.findById(poolId)
            .orElseThrow(() -> new EntityNotFoundException("Budget pool not found"));
        pool.setBudgetAmount(dto.budgetAmount());
        poolRepository.save(pool);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_budget_pool", poolId, null, pool);
        return toPoolDto(pool);
    }

    // --- Proposals (HR view + approval) ---------------------------------------

    @Transactional(readOnly = true)
    public List<ProposalDto> listProposals(UUID cycleId) {
        findCycle(cycleId);
        return proposalRepository.findByCycleIdOrderByCreatedAtAsc(cycleId).stream().map(this::toProposalDto).toList();
    }

    @Transactional
    public ProposalDto approve(UUID proposalId, UUID actorId) {
        CompensationProposal proposal = findProposal(proposalId);
        if (proposal.getStatus() != ProposalStatus.PROPOSED) {
            throw new IllegalStateException("Only proposed entries can be approved");
        }
        proposal.setStatus(ProposalStatus.APPROVED);
        proposal.setApprovedBy(actorId);
        proposalRepository.save(proposal);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_proposal", proposalId, null, "APPROVED");
        return toProposalDto(proposal);
    }

    @Transactional
    public ProposalDto reject(UUID proposalId, UUID actorId) {
        CompensationProposal proposal = findProposal(proposalId);
        if (proposal.getStatus() != ProposalStatus.PROPOSED && proposal.getStatus() != ProposalStatus.APPROVED) {
            throw new IllegalStateException("Only proposed or approved entries can be rejected");
        }
        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setApprovedBy(actorId);
        proposalRepository.save(proposal);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_proposal", proposalId, null, "REJECTED");
        return toProposalDto(proposal);
    }

    // --- Manager surface (scoped to own reports) ------------------------------

    /** Cycles in which the current user manages at least one proposal, newest first. */
    @Transactional(readOnly = true)
    public List<ReviewCycleDto> myCycles(UUID currentUserId) {
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        List<UUID> cycleIds = proposalRepository.findDistinctCycleIdsByManagerEmployeeId(managerEmployeeId);
        if (cycleIds.isEmpty()) {
            return List.of();
        }
        return cycleRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(c -> cycleIds.contains(c.getId()))
            .map(this::toCycleDto)
            .toList();
    }

    /** Budget pools for the departments the current user manages within a cycle. */
    @Transactional(readOnly = true)
    public List<BudgetPoolDto> myPools(UUID cycleId, UUID currentUserId) {
        findCycle(cycleId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        Set<UUID> deptIds = new LinkedHashSet<>(
            proposalRepository.findDistinctDepartmentIdsByCycleIdAndManagerEmployeeId(cycleId, managerEmployeeId));
        return poolRepository.findByCycleId(cycleId).stream()
            .filter(pool -> deptIds.contains(pool.getDepartmentId()))
            .map(this::toPoolDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProposalDto> myProposals(UUID cycleId, UUID currentUserId) {
        findCycle(cycleId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        return proposalRepository
            .findByCycleIdAndManagerEmployeeIdOrderByCreatedAtAsc(cycleId, managerEmployeeId).stream()
            .map(this::toProposalDto).toList();
    }

    /**
     * Manager enters a proposed increase. Provide proposedPercent (wins) or
     * proposedIncreaseAmount; null/zero both clears the proposal back to PENDING.
     * Enforces the proposal's department budget pool (hard guardrail).
     */
    @Transactional
    public ProposalDto saveProposal(UUID proposalId, ProposalUpdateDto dto, UUID currentUserId) {
        CompensationProposal proposal = findProposal(proposalId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        if (!managerEmployeeId.equals(proposal.getManagerEmployeeId())) {
            throw new IllegalStateException("You can only propose changes for your own reports");
        }
        CompensationReviewCycle cycle = findCycle(proposal.getCycleId());
        requireStatus(cycle, ReviewCycleStatus.ACTIVE, "Proposals can only be edited while the cycle is active");

        BigDecimal base = proposal.getCurrentBaseAmount();
        BigDecimal percent = resolvePercent(dto, base);

        if (percent == null || percent.signum() <= 0) {
            proposal.setProposedPercent(null);
            proposal.setProposedIncreaseAmount(null);
            proposal.setProposedBaseAmount(null);
            proposal.setStatus(ProposalStatus.PENDING);
        } else {
            BigDecimal increase = base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            enforceBudget(proposal, increase);
            proposal.setProposedPercent(percent.setScale(2, RoundingMode.HALF_UP));
            proposal.setProposedIncreaseAmount(increase);
            proposal.setProposedBaseAmount(base.add(increase));
            proposal.setStatus(ProposalStatus.PROPOSED);
        }
        if (dto.changeReason() != null) {
            proposal.setChangeReason(dto.changeReason());
        }
        proposal.setNote(trimmedOrNull(dto.note()));
        proposal.setProposedBy(currentUserId);
        proposalRepository.save(proposal);
        auditLogService.log(currentUserId, AuditAction.UPDATE, "compensation_proposal", proposalId, null, proposal);
        return toProposalDto(proposal);
    }

    // --- Internals ------------------------------------------------------------

    private BigDecimal resolvePercent(ProposalUpdateDto dto, BigDecimal base) {
        if (dto.proposedPercent() != null) {
            return dto.proposedPercent();
        }
        if (dto.proposedIncreaseAmount() != null && base.signum() > 0) {
            return dto.proposedIncreaseAmount().multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** Rejects a proposal that would push its department pool over budget (annualized). */
    private void enforceBudget(CompensationProposal proposal, BigDecimal increase) {
        CompensationBudgetPool pool = poolRepository
            .findByCycleIdAndDepartmentId(proposal.getCycleId(), proposal.getDepartmentId())
            .orElse(null);
        if (pool == null) {
            return; // no pool for this department (e.g. ad-hoc) -> no guardrail
        }
        BigDecimal newAnnualIncrease = CompensationService.annualize(increase, proposal.getPayFrequency());
        BigDecimal allocatedOthers = BigDecimal.ZERO;
        for (CompensationProposal other : proposalRepository
                .findByCycleIdAndDepartmentId(proposal.getCycleId(), proposal.getDepartmentId())) {
            if (other.getId().equals(proposal.getId()) || other.getProposedIncreaseAmount() == null) {
                continue;
            }
            if (other.getStatus() == ProposalStatus.PROPOSED || other.getStatus() == ProposalStatus.APPROVED) {
                allocatedOthers = allocatedOthers.add(
                    CompensationService.annualize(other.getProposedIncreaseAmount(), other.getPayFrequency()));
            }
        }
        if (allocatedOthers.add(newAnnualIncrease).compareTo(pool.getBudgetAmount()) > 0) {
            throw new IllegalStateException("Proposal exceeds the remaining department budget");
        }
    }

    private CompensationProposal buildProposal(
            CompensationReviewCycle cycle, Employee employee, CompensationRecord current,
            Map<UUID, Department> deptById) {
        Integer rating = null;
        Integer potential = null;
        if (cycle.getSourcePerformanceCycleId() != null) {
            PerformanceFact fact = performanceFactRepository
                .findFirstByCycleIdAndEmployeeIdOrderByCompletedAtDesc(
                    cycle.getSourcePerformanceCycleId(), employee.getId())
                .orElse(null);
            if (fact != null) {
                rating = fact.getOverallRatingValue();
                potential = fact.getPotentialRatingValue();
            }
        }
        RatingBand ratingBand = MeritMatrixService.bandRating(
            rating, cycle.getRatingLowMax(), cycle.getRatingHighMin());
        CompaBand compaBand = MeritMatrixService.bandCompa(
            current.getCompaRatio(), cycle.getCompaLowMax(), cycle.getCompaHighMin());
        BigDecimal suggested = meritMatrixService.suggestedPercent(ratingBand, compaBand);

        return CompensationProposal.builder()
            .cycleId(cycle.getId())
            .employeeId(employee.getId())
            .departmentId(employee.getDepartmentId())
            .managerEmployeeId(resolveManager(employee, deptById))
            .payGradeId(current.getPayGradeId())
            .currentBaseAmount(current.getBaseAmount())
            .currency(current.getCurrency())
            .payFrequency(current.getPayFrequency())
            .currentCompaRatio(current.getCompaRatio())
            .performanceRatingValue(rating)
            .potentialRatingValue(potential)
            .ratingBand(ratingBand)
            .compaBand(compaBand)
            .suggestedPercent(suggested)
            .changeReason(CompensationChangeReason.MERIT)
            .status(ProposalStatus.PENDING)
            .build();
    }

    /** Same resolution as the performance reviewer: supervisor spine, else dept-head escalation. */
    private UUID resolveManager(Employee employee, Map<UUID, Department> deptById) {
        if (employee.getSupervisorEmployeeId() != null) {
            return employee.getSupervisorEmployeeId();
        }
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
        return null;
    }

    private List<Employee> resolveScope(CompensationReviewCycle cycle, Map<UUID, Department> deptById) {
        List<CompensationReviewCycleDepartment> scope = cycleDepartmentRepository.findByCycleId(cycle.getId());
        if (scope.isEmpty()) {
            return employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        }
        Set<UUID> deptIds = new LinkedHashSet<>();
        for (CompensationReviewCycleDepartment row : scope) {
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

    private void replaceDepartments(UUID cycleId, List<UUID> departmentIds) {
        cycleDepartmentRepository.deleteByCycleId(cycleId);
        if (departmentIds == null) {
            return;
        }
        for (UUID departmentId : new LinkedHashSet<>(departmentIds)) {
            cycleDepartmentRepository.save(CompensationReviewCycleDepartment.builder()
                .cycleId(cycleId)
                .departmentId(departmentId)
                .build());
        }
    }

    private void validateConfig(ReviewCycleCreateDto dto) {
        if (dto.ratingLowMax() >= dto.ratingHighMin()) {
            throw new IllegalArgumentException("Rating low-max must be below rating high-min");
        }
        if (dto.compaLowMax().compareTo(dto.compaHighMin()) > 0) {
            throw new IllegalArgumentException("Compa low-max cannot exceed compa high-min");
        }
    }

    private Employee currentEmployee(UUID currentUserId) {
        return employeeRepository.findByUserId(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("No employee profile for the current user"));
    }

    private CompensationReviewCycle findCycle(UUID id) {
        return cycleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review cycle not found"));
    }

    private CompensationProposal findProposal(UUID id) {
        return proposalRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Proposal not found"));
    }

    private static void requireStatus(CompensationReviewCycle cycle, ReviewCycleStatus expected, String message) {
        if (cycle.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ReviewCycleDto toCycleDto(CompensationReviewCycle cycle) {
        List<UUID> departmentIds = cycleDepartmentRepository.findByCycleId(cycle.getId()).stream()
            .map(CompensationReviewCycleDepartment::getDepartmentId).toList();
        return new ReviewCycleDto(
            cycle.getId(), cycle.getName(), cycle.getStatus(), cycle.getSourcePerformanceCycleId(),
            cycle.getEffectiveDate(), cycle.getDefaultBudgetPercent(), cycle.getRatingLowMax(),
            cycle.getRatingHighMin(), cycle.getCompaLowMax(), cycle.getCompaHighMin(),
            cycle.isIncludeSubDepartments(), departmentIds,
            proposalRepository.countByCycleId(cycle.getId()),
            proposalRepository.countByCycleIdAndStatus(cycle.getId(), ProposalStatus.PROPOSED),
            proposalRepository.countByCycleIdAndStatus(cycle.getId(), ProposalStatus.APPROVED),
            proposalRepository.countByCycleIdAndStatus(cycle.getId(), ProposalStatus.APPLIED),
            proposalRepository.sumProposedIncreaseByCycleIdAndStatus(cycle.getId(), ProposalStatus.APPROVED));
    }

    private BudgetPoolDto toPoolDto(CompensationBudgetPool pool) {
        List<CompensationProposal> deptProposals =
            proposalRepository.findByCycleIdAndDepartmentId(pool.getCycleId(), pool.getDepartmentId());
        BigDecimal allocated = BigDecimal.ZERO;
        long proposalCount = 0;
        for (CompensationProposal p : deptProposals) {
            proposalCount++;
            if (p.getProposedIncreaseAmount() != null
                    && (p.getStatus() == ProposalStatus.PROPOSED
                        || p.getStatus() == ProposalStatus.APPROVED
                        || p.getStatus() == ProposalStatus.APPLIED)) {
                allocated = allocated.add(
                    CompensationService.annualize(p.getProposedIncreaseAmount(), p.getPayFrequency()));
            }
        }
        return new BudgetPoolDto(
            pool.getId(), pool.getCycleId(), pool.getDepartmentId(), departmentName(pool.getDepartmentId()),
            pool.getBasePayroll(), pool.getBudgetPercent(), pool.getBudgetAmount(),
            allocated, pool.getBudgetAmount().subtract(allocated), proposalCount);
    }

    private ProposalDto toProposalDto(CompensationProposal p) {
        PayGrade grade = p.getPayGradeId() == null ? null
            : payGradeRepository.findById(p.getPayGradeId()).orElse(null);
        BigDecimal proposedCompa = p.getProposedBaseAmount() == null ? null
            : compensationService.computeCompaRatio(p.getProposedBaseAmount(), p.getPayFrequency(), grade);
        return new ProposalDto(
            p.getId(), p.getCycleId(), p.getEmployeeId(), employeeName(p.getEmployeeId()),
            p.getDepartmentId(), departmentName(p.getDepartmentId()), p.getManagerEmployeeId(),
            p.getPayGradeId(), grade == null ? null : grade.getCode(),
            p.getCurrentBaseAmount(), p.getCurrency(), p.getPayFrequency(), p.getCurrentCompaRatio(),
            p.getPerformanceRatingValue(), p.getPotentialRatingValue(), p.getRatingBand(), p.getCompaBand(),
            p.getSuggestedPercent(), p.getProposedPercent(), p.getProposedIncreaseAmount(),
            p.getProposedBaseAmount(), proposedCompa, p.getChangeReason(), p.getStatus(),
            p.getNote(), p.getAppliedRecordId());
    }

    private String departmentName(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
    }

    private String employeeName(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return null;
        }
        return userRepository.findById(employee.getUserId())
            .map(u -> ((safe(u.getFirstName()) + " " + safe(u.getLastName())).trim()))
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
