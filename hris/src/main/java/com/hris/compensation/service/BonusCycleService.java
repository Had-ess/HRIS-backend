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
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardUpdateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusCycleCreateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusCycleDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPoolDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPoolUpdateDto;
import com.hris.compensation.dto.CompensationBonusDtos.SpotAwardCreateDto;
import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.entity.BonusCycle;
import com.hris.compensation.entity.BonusCycleDepartment;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.entity.BonusPool;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusCycleDepartmentRepository;
import com.hris.compensation.repository.BonusCycleRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import com.hris.compensation.repository.BonusPoolRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * Variable / bonus pay. Bonus cycles mirror the merit cycle: they generate one
 * award per in-scope employee using the full STI formula
 * (target% x annualized base x performance factor x company funding factor),
 * draw them down against per-department pools, route through a single HR approval
 * gate, and on apply mark awards PAID. Bonus is append-only and never writes to
 * compensation_records. Also supports ad-hoc SPOT awards outside any cycle.
 */
@Service
@RequiredArgsConstructor
public class BonusCycleService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BonusCycleRepository cycleRepository;
    private final BonusCycleDepartmentRepository cycleDepartmentRepository;
    private final BonusPoolRepository poolRepository;
    private final BonusAwardRepository awardRepository;
    private final BonusPlanRepository planRepository;
    private final CompensationRecordRepository recordRepository;
    private final PerformanceFactRepository performanceFactRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // --- Cycles (HR) ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BonusCycleDto> getAll() {
        return cycleRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toCycleDto).toList();
    }

    @Transactional(readOnly = true)
    public BonusCycleDto get(UUID id) {
        return toCycleDto(findCycle(id));
    }

    @Transactional
    public BonusCycleDto create(BonusCycleCreateDto dto, UUID actorId) {
        validateConfig(dto);
        BonusPlan plan = findPlan(dto.bonusPlanId());
        BonusCycle cycle = cycleRepository.save(BonusCycle.builder()
            .name(dto.name().trim())
            .status(ReviewCycleStatus.DRAFT)
            .bonusPlanId(plan.getId())
            .sourcePerformanceCycleId(dto.sourcePerformanceCycleId())
            .payoutDate(dto.payoutDate())
            .companyFundingFactor(dto.companyFundingFactor())
            .ratingLowMax(dto.ratingLowMax())
            .ratingHighMin(dto.ratingHighMin())
            .perfFactorLow(dto.perfFactorLow())
            .perfFactorSolid(dto.perfFactorSolid())
            .perfFactorHigh(dto.perfFactorHigh())
            .includeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()))
            .createdBy(actorId)
            .build());
        replaceDepartments(cycle.getId(), dto.departmentIds());
        auditLogService.log(actorId, AuditAction.CREATE, "compensation_bonus_cycle", cycle.getId(), null, cycle);
        return toCycleDto(cycle);
    }

    @Transactional
    public BonusCycleDto update(UUID id, BonusCycleCreateDto dto, UUID actorId) {
        BonusCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.DRAFT, "Only draft cycles can be edited");
        validateConfig(dto);
        BonusPlan plan = findPlan(dto.bonusPlanId());
        cycle.setName(dto.name().trim());
        cycle.setBonusPlanId(plan.getId());
        cycle.setSourcePerformanceCycleId(dto.sourcePerformanceCycleId());
        cycle.setPayoutDate(dto.payoutDate());
        cycle.setCompanyFundingFactor(dto.companyFundingFactor());
        cycle.setRatingLowMax(dto.ratingLowMax());
        cycle.setRatingHighMin(dto.ratingHighMin());
        cycle.setPerfFactorLow(dto.perfFactorLow());
        cycle.setPerfFactorSolid(dto.perfFactorSolid());
        cycle.setPerfFactorHigh(dto.perfFactorHigh());
        cycle.setIncludeSubDepartments(Boolean.TRUE.equals(dto.includeSubDepartments()));
        cycleRepository.save(cycle);
        replaceDepartments(id, dto.departmentIds());
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_cycle", id, null, cycle);
        return toCycleDto(cycle);
    }

    /** DRAFT -> ACTIVE: snapshot per-department pools and generate awards. */
    @Transactional
    public BonusCycleDto activate(UUID id, UUID actorId) {
        BonusCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.DRAFT, "Only draft cycles can be activated");
        BonusPlan plan = findPlan(cycle.getBonusPlanId());

        Map<UUID, Department> deptById = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<Employee> employees = resolveScope(cycle, deptById);

        Map<UUID, BigDecimal> payrollByDept = new HashMap<>();
        Map<UUID, BigDecimal> targetByDept = new HashMap<>();
        for (Employee employee : employees) {
            if (awardRepository.existsByCycleIdAndEmployeeId(cycle.getId(), employee.getId())) {
                continue;
            }
            CompensationRecord current = recordRepository
                .findByEmployeeIdAndIsCurrentTrue(employee.getId()).orElse(null);
            if (current == null) {
                continue; // no current pay -> not part of the bonus cycle
            }
            BonusAward award = buildAward(cycle, plan, employee, current, deptById);
            awardRepository.save(award);
            BigDecimal annualBase = CompensationService.annualize(current.getBaseAmount(), current.getPayFrequency());
            payrollByDept.merge(employee.getDepartmentId(), annualBase, BigDecimal::add);
            targetByDept.merge(employee.getDepartmentId(), award.getSuggestedAmount(), BigDecimal::add);
        }

        if (!poolRepository.existsByCycleId(cycle.getId())) {
            for (Map.Entry<UUID, BigDecimal> entry : payrollByDept.entrySet()) {
                BigDecimal target = targetByDept.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                poolRepository.save(BonusPool.builder()
                    .cycleId(cycle.getId())
                    .departmentId(entry.getKey())
                    .basePayroll(entry.getValue())
                    .targetAmount(target)
                    .budgetAmount(target)
                    .build());
            }
        }

        cycle.setStatus(ReviewCycleStatus.ACTIVE);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_cycle", id, null, "ACTIVATED");
        return toCycleDto(cycle);
    }

    /** ACTIVE -> IN_REVIEW: lock manager input for HR approval. */
    @Transactional
    public BonusCycleDto advanceToReview(UUID id, UUID actorId) {
        BonusCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.ACTIVE, "Only active cycles can advance to review");
        cycle.setStatus(ReviewCycleStatus.IN_REVIEW);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_cycle", id, null, "IN_REVIEW");
        return toCycleDto(cycle);
    }

    /** IN_REVIEW -> CLOSED: mark every APPROVED award PAID. No comp-record write. */
    @Transactional
    public BonusCycleDto applyAndClose(UUID id, UUID actorId) {
        BonusCycle cycle = findCycle(id);
        requireStatus(cycle, ReviewCycleStatus.IN_REVIEW, "Only in-review cycles can be applied");

        for (BonusAward award : awardRepository.findByCycleIdAndStatus(id, BonusAwardStatus.APPROVED)) {
            award.setStatus(BonusAwardStatus.PAID);
            award.setPayoutDate(cycle.getPayoutDate());
            awardRepository.save(award);
            auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_award", award.getId(), null, "PAID");
        }

        cycle.setStatus(ReviewCycleStatus.CLOSED);
        cycleRepository.save(cycle);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_cycle", id, null, "CLOSED");
        return toCycleDto(cycle);
    }

    // --- Bonus pools (HR) -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<BonusPoolDto> getPools(UUID cycleId) {
        findCycle(cycleId);
        return poolRepository.findByCycleId(cycleId).stream().map(this::toPoolDto).toList();
    }

    @Transactional
    public BonusPoolDto updatePool(UUID poolId, BonusPoolUpdateDto dto, UUID actorId) {
        BonusPool pool = poolRepository.findById(poolId)
            .orElseThrow(() -> new EntityNotFoundException("Bonus pool not found"));
        pool.setBudgetAmount(dto.budgetAmount());
        poolRepository.save(pool);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_pool", poolId, null, pool);
        return toPoolDto(pool);
    }

    // --- Awards (HR view + approval) ------------------------------------------

    @Transactional(readOnly = true)
    public List<BonusAwardDto> listAwards(UUID cycleId) {
        findCycle(cycleId);
        return awardRepository.findByCycleIdOrderByCreatedAtAsc(cycleId).stream().map(this::toAwardDto).toList();
    }

    @Transactional
    public BonusAwardDto approve(UUID awardId, UUID actorId) {
        BonusAward award = findAward(awardId);
        if (award.getStatus() != BonusAwardStatus.PROPOSED) {
            throw new IllegalStateException("Only proposed awards can be approved");
        }
        award.setStatus(BonusAwardStatus.APPROVED);
        award.setApprovedBy(actorId);
        awardRepository.save(award);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_award", awardId, null, "APPROVED");
        return toAwardDto(award);
    }

    @Transactional
    public BonusAwardDto reject(UUID awardId, UUID actorId) {
        BonusAward award = findAward(awardId);
        if (award.getStatus() != BonusAwardStatus.PROPOSED && award.getStatus() != BonusAwardStatus.APPROVED) {
            throw new IllegalStateException("Only proposed or approved awards can be rejected");
        }
        award.setStatus(BonusAwardStatus.REJECTED);
        award.setApprovedBy(actorId);
        awardRepository.save(award);
        auditLogService.log(actorId, AuditAction.UPDATE, "compensation_bonus_award", awardId, null, "REJECTED");
        return toAwardDto(award);
    }

    /** HR grants an ad-hoc one-time spot bonus (no cycle, no pool guardrail). */
    @Transactional
    public BonusAwardDto grantSpot(SpotAwardCreateDto dto, UUID actorId) {
        Employee employee = employeeRepository.findById(dto.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        CompensationRecord current = recordRepository
            .findByEmployeeIdAndIsCurrentTrue(employee.getId()).orElse(null);
        if (current == null) {
            throw new IllegalStateException("Employee has no current compensation record");
        }
        BonusAward award = awardRepository.save(BonusAward.builder()
            .cycleId(null)
            .employeeId(employee.getId())
            .departmentId(employee.getDepartmentId())
            .managerEmployeeId(employee.getSupervisorEmployeeId())
            .bonusPlanId(dto.bonusPlanId())
            .awardType(BonusAwardType.SPOT)
            .currentBaseAmount(current.getBaseAmount())
            .currency(current.getCurrency())
            .payFrequency(current.getPayFrequency())
            .targetPercent(BigDecimal.ZERO)
            .performanceFactor(BigDecimal.ONE)
            .companyFactor(BigDecimal.ONE)
            .suggestedAmount(dto.awardedAmount())
            .awardedAmount(dto.awardedAmount())
            .payoutDate(dto.payoutDate())
            .status(BonusAwardStatus.APPROVED)
            .note(trimmedOrNull(dto.note()))
            .proposedBy(actorId)
            .approvedBy(actorId)
            .build());
        auditLogService.log(actorId, AuditAction.CREATE, "compensation_bonus_award", award.getId(), null, award);
        return toAwardDto(award);
    }

    // --- Manager surface (scoped to own reports) ------------------------------

    @Transactional(readOnly = true)
    public List<BonusCycleDto> myCycles(UUID currentUserId) {
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        List<UUID> cycleIds = awardRepository.findDistinctCycleIdsByManagerEmployeeId(managerEmployeeId);
        if (cycleIds.isEmpty()) {
            return List.of();
        }
        return cycleRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(c -> cycleIds.contains(c.getId()))
            .map(this::toCycleDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BonusPoolDto> myPools(UUID cycleId, UUID currentUserId) {
        findCycle(cycleId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        Set<UUID> deptIds = new LinkedHashSet<>(
            awardRepository.findDistinctDepartmentIdsByCycleIdAndManagerEmployeeId(cycleId, managerEmployeeId));
        return poolRepository.findByCycleId(cycleId).stream()
            .filter(pool -> deptIds.contains(pool.getDepartmentId()))
            .map(this::toPoolDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BonusAwardDto> myAwards(UUID cycleId, UUID currentUserId) {
        findCycle(cycleId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        return awardRepository
            .findByCycleIdAndManagerEmployeeIdOrderByCreatedAtAsc(cycleId, managerEmployeeId).stream()
            .map(this::toAwardDto).toList();
    }

    /**
     * Manager sets the awarded amount on a cycle award. Null/zero clears it back to
     * PENDING. Enforces the award's department bonus pool (hard guardrail).
     */
    @Transactional
    public BonusAwardDto saveAward(UUID awardId, BonusAwardUpdateDto dto, UUID currentUserId) {
        BonusAward award = findAward(awardId);
        UUID managerEmployeeId = currentEmployee(currentUserId).getId();
        if (!managerEmployeeId.equals(award.getManagerEmployeeId())) {
            throw new IllegalStateException("You can only adjust awards for your own reports");
        }
        if (award.getCycleId() == null) {
            throw new IllegalStateException("Spot awards cannot be edited from the worksheet");
        }
        BonusCycle cycle = findCycle(award.getCycleId());
        requireStatus(cycle, ReviewCycleStatus.ACTIVE, "Awards can only be edited while the cycle is active");

        BigDecimal amount = dto.awardedAmount();
        if (amount == null || amount.signum() <= 0) {
            award.setAwardedAmount(BigDecimal.ZERO);
            award.setStatus(BonusAwardStatus.PENDING);
        } else {
            BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
            enforceBudget(award, scaled);
            award.setAwardedAmount(scaled);
            award.setStatus(BonusAwardStatus.PROPOSED);
        }
        award.setNote(trimmedOrNull(dto.note()));
        award.setProposedBy(currentUserId);
        awardRepository.save(award);
        auditLogService.log(currentUserId, AuditAction.UPDATE, "compensation_bonus_award", awardId, null, award);
        return toAwardDto(award);
    }

    // --- Internals ------------------------------------------------------------

    /** Rejects an award that would push its department pool over budget (absolute amounts). */
    private void enforceBudget(BonusAward award, BigDecimal amount) {
        BonusPool pool = poolRepository
            .findByCycleIdAndDepartmentId(award.getCycleId(), award.getDepartmentId())
            .orElse(null);
        if (pool == null) {
            return;
        }
        BigDecimal allocatedOthers = BigDecimal.ZERO;
        for (BonusAward other : awardRepository
                .findByCycleIdAndDepartmentId(award.getCycleId(), award.getDepartmentId())) {
            if (other.getId().equals(award.getId()) || other.getAwardedAmount() == null) {
                continue;
            }
            if (isAllocated(other.getStatus())) {
                allocatedOthers = allocatedOthers.add(other.getAwardedAmount());
            }
        }
        if (allocatedOthers.add(amount).compareTo(pool.getBudgetAmount()) > 0) {
            throw new IllegalStateException("Award exceeds the remaining department budget");
        }
    }

    private static boolean isAllocated(BonusAwardStatus status) {
        return status == BonusAwardStatus.PROPOSED
            || status == BonusAwardStatus.APPROVED
            || status == BonusAwardStatus.PAID;
    }

    private BonusAward buildAward(
            BonusCycle cycle, BonusPlan plan, Employee employee, CompensationRecord current,
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
        BigDecimal perfFactor = perfFactorFor(ratingBand, cycle);
        BigDecimal companyFactor = cycle.getCompanyFundingFactor();
        BigDecimal annualBase = CompensationService.annualize(current.getBaseAmount(), current.getPayFrequency());
        BigDecimal suggested = computeAward(annualBase, plan.getTargetPercent(), perfFactor, companyFactor);

        return BonusAward.builder()
            .cycleId(cycle.getId())
            .employeeId(employee.getId())
            .departmentId(employee.getDepartmentId())
            .managerEmployeeId(resolveManager(employee, deptById))
            .bonusPlanId(plan.getId())
            .awardType(BonusAwardType.CYCLE)
            .currentBaseAmount(current.getBaseAmount())
            .currency(current.getCurrency())
            .payFrequency(current.getPayFrequency())
            .targetPercent(plan.getTargetPercent())
            .performanceRatingValue(rating)
            .potentialRatingValue(potential)
            .ratingBand(ratingBand)
            .performanceFactor(perfFactor)
            .companyFactor(companyFactor)
            .suggestedAmount(suggested)
            .awardedAmount(suggested)
            .status(BonusAwardStatus.PENDING)
            .build();
    }

    /** award = target%/100 x annualized base x performance factor x company funding factor. */
    private static BigDecimal computeAward(
            BigDecimal annualBase, BigDecimal targetPercent, BigDecimal perfFactor, BigDecimal companyFactor) {
        return annualBase.multiply(targetPercent).divide(HUNDRED, 10, RoundingMode.HALF_UP)
            .multiply(perfFactor)
            .multiply(companyFactor)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal perfFactorFor(RatingBand band, BonusCycle cycle) {
        return switch (band) {
            case LOW -> cycle.getPerfFactorLow();
            case HIGH -> cycle.getPerfFactorHigh();
            default -> cycle.getPerfFactorSolid();
        };
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

    private List<Employee> resolveScope(BonusCycle cycle, Map<UUID, Department> deptById) {
        List<BonusCycleDepartment> scope = cycleDepartmentRepository.findByCycleId(cycle.getId());
        if (scope.isEmpty()) {
            return employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        }
        Set<UUID> deptIds = new LinkedHashSet<>();
        for (BonusCycleDepartment row : scope) {
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
            cycleDepartmentRepository.save(BonusCycleDepartment.builder()
                .cycleId(cycleId)
                .departmentId(departmentId)
                .build());
        }
    }

    private void validateConfig(BonusCycleCreateDto dto) {
        if (dto.ratingLowMax() >= dto.ratingHighMin()) {
            throw new IllegalArgumentException("Rating low-max must be below rating high-min");
        }
    }

    private Employee currentEmployee(UUID currentUserId) {
        return employeeRepository.findByUserId(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("No employee profile for the current user"));
    }

    private BonusCycle findCycle(UUID id) {
        return cycleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Bonus cycle not found"));
    }

    private BonusPlan findPlan(UUID id) {
        return planRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Bonus plan not found"));
    }

    private BonusAward findAward(UUID id) {
        return awardRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Bonus award not found"));
    }

    private static void requireStatus(BonusCycle cycle, ReviewCycleStatus expected, String message) {
        if (cycle.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BonusCycleDto toCycleDto(BonusCycle cycle) {
        List<UUID> departmentIds = cycleDepartmentRepository.findByCycleId(cycle.getId()).stream()
            .map(BonusCycleDepartment::getDepartmentId).toList();
        BonusPlan plan = planRepository.findById(cycle.getBonusPlanId()).orElse(null);
        return new BonusCycleDto(
            cycle.getId(), cycle.getName(), cycle.getStatus(), cycle.getBonusPlanId(),
            plan == null ? null : plan.getName(), plan == null ? null : plan.getTargetPercent(),
            cycle.getSourcePerformanceCycleId(), cycle.getPayoutDate(), cycle.getCompanyFundingFactor(),
            cycle.getRatingLowMax(), cycle.getRatingHighMin(),
            cycle.getPerfFactorLow(), cycle.getPerfFactorSolid(), cycle.getPerfFactorHigh(),
            cycle.isIncludeSubDepartments(), departmentIds,
            awardRepository.countByCycleId(cycle.getId()),
            awardRepository.countByCycleIdAndStatus(cycle.getId(), BonusAwardStatus.PROPOSED),
            awardRepository.countByCycleIdAndStatus(cycle.getId(), BonusAwardStatus.APPROVED),
            awardRepository.countByCycleIdAndStatus(cycle.getId(), BonusAwardStatus.PAID),
            awardRepository.sumAwardedByCycleIdAndStatus(cycle.getId(), BonusAwardStatus.APPROVED));
    }

    private BonusPoolDto toPoolDto(BonusPool pool) {
        List<BonusAward> deptAwards =
            awardRepository.findByCycleIdAndDepartmentId(pool.getCycleId(), pool.getDepartmentId());
        BigDecimal allocated = BigDecimal.ZERO;
        long awardCount = 0;
        for (BonusAward a : deptAwards) {
            awardCount++;
            if (a.getAwardedAmount() != null && isAllocated(a.getStatus())) {
                allocated = allocated.add(a.getAwardedAmount());
            }
        }
        return new BonusPoolDto(
            pool.getId(), pool.getCycleId(), pool.getDepartmentId(), departmentName(pool.getDepartmentId()),
            pool.getBasePayroll(), pool.getTargetAmount(), pool.getBudgetAmount(),
            allocated, pool.getBudgetAmount().subtract(allocated), awardCount);
    }

    private BonusAwardDto toAwardDto(BonusAward a) {
        return new BonusAwardDto(
            a.getId(), a.getCycleId(), a.getEmployeeId(), employeeName(a.getEmployeeId()),
            a.getDepartmentId(), departmentName(a.getDepartmentId()), a.getManagerEmployeeId(),
            a.getBonusPlanId(), a.getAwardType(), a.getCurrentBaseAmount(), a.getCurrency(), a.getPayFrequency(),
            a.getTargetPercent(), a.getPerformanceRatingValue(), a.getPotentialRatingValue(), a.getRatingBand(),
            a.getPerformanceFactor(), a.getCompanyFactor(), a.getSuggestedAmount(), a.getAwardedAmount(),
            a.getPayoutDate(), a.getStatus(), a.getNote());
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
