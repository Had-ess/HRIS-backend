package com.hris.analytics.service;

import com.hris.analytics.entity.*;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.analytics.enums.RiskLevel;
import com.hris.analytics.repository.*;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.leave.enums.LeaveStatus;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsAggregationService {

    private final LeaveFactRepository leaveFactRepository;
    private final ApprovalFactRepository approvalFactRepository;
    private final HeadcountFactRepository headcountFactRepository;
    private final ProjectAbsenceFactRepository projectAbsenceFactRepository;
    private final LeaveMetricsSnapshotRepository leaveMetricsSnapshotRepository;
    private final HeadcountMetricsSnapshotRepository headcountMetricsSnapshotRepository;
    private final LeaveDistributionSnapshotRepository leaveDistributionSnapshotRepository;
    private final ApprovalBottleneckSnapshotRepository approvalBottleneckSnapshotRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ApprovalStepRepository approvalStepRepository;

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "analyticsAggregationJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void rebuildCurrentSnapshots() {
        LocalDate today = LocalDate.now();
        rebuildHeadcountFacts(today);
        rebuildProjectAbsenceFacts(today);
        rebuildLeaveMetrics(today);
        rebuildHeadcountMetrics(today);
        rebuildLeaveDistribution(today);
        rebuildApprovalBottlenecks(today);
    }

    @Transactional
    public void rebuildHeadcountFacts(LocalDate snapshotDate) {
        headcountFactRepository.deleteBySnapshotDate(snapshotDate);
        for (Employee employee : employeeRepository.findAll()) {
            UUID teamId = resolveCurrentTeamId(employee.getId(), snapshotDate);
            headcountFactRepository.save(HeadcountFact.builder()
                .snapshotDate(snapshotDate)
                .employeeId(employee.getId())
                .departmentId(employee.getDepartmentId())
                .teamId(teamId)
                .employeeStatus(employee.getStatus())
                .active(employee.getStatus() == EmployeeStatus.ACTIVE)
                .build());
        }
    }

    @Transactional
    public void rebuildProjectAbsenceFacts(LocalDate snapshotDate) {
        projectAbsenceFactRepository.deleteBySnapshotDate(snapshotDate);
        Map<String, ProjectAbsenceAccumulator> grouped = new LinkedHashMap<>();

        for (LeaveFact fact : leaveFactRepository.findByEventDateBetween(snapshotDate.minusDays(365), snapshotDate)) {
            if (fact.getRequestStatus() != LeaveStatus.APPROVED || fact.getProjectId() == null) {
                continue;
            }
            String key = fact.getProjectId() + ":" + fact.getTeamId();
            grouped.computeIfAbsent(key, ignored -> new ProjectAbsenceAccumulator(fact.getProjectId(), fact.getTeamId()))
                .add(fact);
        }

        grouped.values().forEach(acc -> projectAbsenceFactRepository.save(acc.toEntity(snapshotDate)));
    }

    @Transactional
    public void rebuildLeaveMetrics(LocalDate snapshotDate) {
        leaveMetricsSnapshotRepository.deleteBySnapshotDate(snapshotDate);
        List<LeaveFact> facts = leaveFactRepository.findByEventDate(snapshotDate);
        buildScopeKeys(facts).forEach(scope -> leaveMetricsSnapshotRepository.save(buildLeaveMetricsSnapshot(snapshotDate, scope, facts)));
    }

    @Transactional
    public void rebuildHeadcountMetrics(LocalDate snapshotDate) {
        headcountMetricsSnapshotRepository.deleteBySnapshotDate(snapshotDate);
        List<HeadcountFact> facts = headcountFactRepository.findBySnapshotDate(snapshotDate);
        buildHeadcountScopeKeys(facts).forEach(scope -> headcountMetricsSnapshotRepository.save(buildHeadcountSnapshot(snapshotDate, scope, facts)));
    }

    @Transactional
    public void rebuildLeaveDistribution(LocalDate snapshotDate) {
        leaveDistributionSnapshotRepository.deleteBySnapshotDate(snapshotDate);
        List<LeaveFact> facts = leaveFactRepository.findByEventDate(snapshotDate);
        for (ScopeKey scope : buildScopeKeys(facts)) {
            Map<UUID, List<LeaveFact>> byLeaveType = filterLeaveFactsByScope(facts, scope).stream()
                .collect(java.util.stream.Collectors.groupingBy(LeaveFact::getLeaveTypeId));
            byLeaveType.forEach((leaveTypeId, scopedFacts) -> leaveDistributionSnapshotRepository.save(
                LeaveDistributionSnapshot.builder()
                    .snapshotDate(snapshotDate)
                    .scopeType(scope.type())
                    .scopeId(scope.scopeId())
                    .leaveTypeId(leaveTypeId)
                    .requestCount(scopedFacts.size())
                    .totalDays(scopedFacts.stream().mapToInt(LeaveFact::getWorkingDays).sum())
                    .build()
            ));
        }
    }

    @Transactional
    public void rebuildApprovalBottlenecks(LocalDate snapshotDate) {
        approvalBottleneckSnapshotRepository.deleteBySnapshotDate(snapshotDate);
        List<ApprovalFact> facts = approvalFactRepository.findByEventDate(snapshotDate);
        List<ApprovalStep> pendingSteps = approvalStepRepository.findByStatus(StepStatus.PENDING);

        Map<ApprovalKey, List<ApprovalFact>> groupedFacts = facts.stream()
            .collect(java.util.stream.Collectors.groupingBy(f -> new ApprovalKey(f.getSourceType(), f.getApproverLevel())));
        Map<ApprovalKey, Long> pendingCounts = pendingSteps.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                step -> new ApprovalKey(
                    step.getSourceType() != null ? step.getSourceType() : ApprovalSourceType.TEAM_CHAIN,
                    step.getApproverLevel() != null ? step.getApproverLevel() : 1
                ),
                java.util.stream.Collectors.counting()
            ));

        Set<ApprovalKey> keys = new LinkedHashSet<>();
        keys.addAll(groupedFacts.keySet());
        keys.addAll(pendingCounts.keySet());

        for (ApprovalKey key : keys) {
            List<ApprovalFact> scopedFacts = groupedFacts.getOrDefault(key, List.of());
            long rejected = scopedFacts.stream().filter(f -> f.getStepStatus() == StepStatus.REJECTED).count();
            BigDecimal rejectionRate = scopedFacts.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(rejected)
                    .divide(BigDecimal.valueOf(scopedFacts.size()), 2, RoundingMode.HALF_UP);
            BigDecimal averageDecisionDays = scopedFacts.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(scopedFacts.stream().mapToInt(ApprovalFact::getDecisionDelayDays).average().orElse(0.0))
                    .setScale(2, RoundingMode.HALF_UP);

            approvalBottleneckSnapshotRepository.save(ApprovalBottleneckSnapshot.builder()
                .snapshotDate(snapshotDate)
                .scopeType(AnalyticsScopeType.GLOBAL)
                .scopeId(null)
                .sourceType(key.sourceType())
                .approverLevel(key.approverLevel())
                .pendingCount(pendingCounts.getOrDefault(key, 0L).intValue())
                .averageDecisionDays(averageDecisionDays)
                .rejectionRate(rejectionRate)
                .build());
        }
    }

    private LeaveMetricsSnapshot buildLeaveMetricsSnapshot(LocalDate snapshotDate, ScopeKey scope, List<LeaveFact> facts) {
        List<LeaveFact> scopedFacts = filterLeaveFactsByScope(facts, scope);
        int total = scopedFacts.size();
        int approved = (int) scopedFacts.stream().filter(f -> f.getRequestStatus() == LeaveStatus.APPROVED).count();
        int rejected = (int) scopedFacts.stream().filter(f -> f.getRequestStatus() == LeaveStatus.REJECTED).count();
        int pending = (int) scopedFacts.stream().filter(f -> f.getRequestStatus() == LeaveStatus.PENDING || f.getRequestStatus() == LeaveStatus.IN_APPROVAL).count();
        BigDecimal average = total == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(scopedFacts.stream().mapToInt(LeaveFact::getApprovalDurationDays).average().orElse(0.0))
                .setScale(2, RoundingMode.HALF_UP);

        return LeaveMetricsSnapshot.builder()
            .snapshotDate(snapshotDate)
            .scopeType(scope.type())
            .scopeId(scope.scopeId())
            .totalRequests(total)
            .approvedCount(approved)
            .rejectedCount(rejected)
            .pendingCount(pending)
            .averageProcessingDays(average)
            .build();
    }

    private HeadcountMetricsSnapshot buildHeadcountSnapshot(LocalDate snapshotDate, ScopeKey scope, List<HeadcountFact> facts) {
        List<HeadcountFact> scopedFacts = filterHeadcountFactsByScope(facts, scope);
        int total = scopedFacts.size();
        int active = (int) scopedFacts.stream().filter(HeadcountFact::isActive).count();
        int newHires = (int) employeeRepository.findAll().stream()
            .filter(employee -> snapshotDate.equals(employee.getHireDate()))
            .filter(employee -> scope.matches(employee.getDepartmentId(), null, employee.getId()))
            .count();
        int terminated = (int) employeeRepository.findAll().stream()
            .filter(employee -> employee.getTerminationDate() != null && snapshotDate.equals(employee.getTerminationDate()))
            .filter(employee -> scope.matches(employee.getDepartmentId(), null, employee.getId()))
            .count();

        return HeadcountMetricsSnapshot.builder()
            .snapshotDate(snapshotDate)
            .scopeType(scope.type())
            .scopeId(scope.scopeId())
            .totalEmployees(total)
            .activeEmployees(active)
            .newHires(newHires)
            .terminatedEmployees(terminated)
            .build();
    }

    private List<ScopeKey> buildScopeKeys(List<LeaveFact> facts) {
        LinkedHashSet<ScopeKey> keys = new LinkedHashSet<>();
        keys.add(new ScopeKey(AnalyticsScopeType.GLOBAL, null));
        for (LeaveFact fact : facts) {
            keys.add(new ScopeKey(AnalyticsScopeType.EMPLOYEE, fact.getEmployeeId()));
            if (fact.getDepartmentId() != null) keys.add(new ScopeKey(AnalyticsScopeType.DEPARTMENT, fact.getDepartmentId()));
            if (fact.getProjectId() != null) keys.add(new ScopeKey(AnalyticsScopeType.PROJECT, fact.getProjectId()));
            if (fact.getTeamId() != null) keys.add(new ScopeKey(AnalyticsScopeType.TEAM, fact.getTeamId()));
        }
        return List.copyOf(keys);
    }

    private List<ScopeKey> buildHeadcountScopeKeys(List<HeadcountFact> facts) {
        LinkedHashSet<ScopeKey> keys = new LinkedHashSet<>();
        keys.add(new ScopeKey(AnalyticsScopeType.GLOBAL, null));
        for (HeadcountFact fact : facts) {
            keys.add(new ScopeKey(AnalyticsScopeType.EMPLOYEE, fact.getEmployeeId()));
            if (fact.getDepartmentId() != null) keys.add(new ScopeKey(AnalyticsScopeType.DEPARTMENT, fact.getDepartmentId()));
            if (fact.getTeamId() != null) keys.add(new ScopeKey(AnalyticsScopeType.TEAM, fact.getTeamId()));
        }
        return List.copyOf(keys);
    }

    private List<LeaveFact> filterLeaveFactsByScope(List<LeaveFact> facts, ScopeKey scope) {
        return facts.stream()
            .filter(fact -> scope.matches(fact.getDepartmentId(), fact.getProjectId(), fact.getEmployeeId(), fact.getTeamId()))
            .toList();
    }

    private List<HeadcountFact> filterHeadcountFactsByScope(List<HeadcountFact> facts, ScopeKey scope) {
        return facts.stream()
            .filter(fact -> scope.matches(fact.getDepartmentId(), null, fact.getEmployeeId(), fact.getTeamId()))
            .toList();
    }

    private UUID resolveCurrentTeamId(UUID employeeId, LocalDate snapshotDate) {
        return projectAssignmentRepository.findByEmployeeIdAndIsActiveTrue(employeeId).stream()
            .filter(assignment -> assignment.getStartDate() != null && !assignment.getStartDate().isAfter(snapshotDate))
            .filter(assignment -> assignment.getEndDate() == null || !assignment.getEndDate().isBefore(snapshotDate))
            .map(ProjectAssignment::getTeamId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private RiskLevel toRiskLevel(int estimatedDelayDays) {
        if (estimatedDelayDays >= 3) {
            return RiskLevel.HIGH;
        }
        if (estimatedDelayDays >= 1) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private record ScopeKey(AnalyticsScopeType type, UUID scopeId) {
        boolean matches(UUID departmentId, UUID projectId, UUID employeeId) {
            return matches(departmentId, projectId, employeeId, null);
        }

        boolean matches(UUID departmentId, UUID projectId, UUID employeeId, UUID teamId) {
            return switch (type) {
                case GLOBAL -> true;
                case DEPARTMENT -> Objects.equals(scopeId, departmentId);
                case PROJECT -> Objects.equals(scopeId, projectId);
                case TEAM -> Objects.equals(scopeId, teamId);
                case EMPLOYEE -> Objects.equals(scopeId, employeeId);
            };
        }
    }

    private record ApprovalKey(ApprovalSourceType sourceType, int approverLevel) {
    }

    private final class ProjectAbsenceAccumulator {
        private final UUID projectId;
        private final UUID teamId;
        private final Set<UUID> employees = new LinkedHashSet<>();
        private int absenceDays;

        private ProjectAbsenceAccumulator(UUID projectId, UUID teamId) {
            this.projectId = projectId;
            this.teamId = teamId;
        }

        private void add(LeaveFact fact) {
            employees.add(fact.getEmployeeId());
            absenceDays += fact.getWorkingDays();
        }

        private ProjectAbsenceFact toEntity(LocalDate snapshotDate) {
            int affectedMembers = employees.size();
            int estimatedDelayDays = affectedMembers == 0 ? 0 : Math.max(1, absenceDays / affectedMembers);
            return ProjectAbsenceFact.builder()
                .snapshotDate(snapshotDate)
                .projectId(projectId)
                .teamId(teamId)
                .absentEmployees(employees.size())
                .absenceDays(absenceDays)
                .affectedMembers(affectedMembers)
                .estimatedDelayDays(estimatedDelayDays)
                .riskLevel(toRiskLevel(estimatedDelayDays))
                .build();
        }
    }
}
