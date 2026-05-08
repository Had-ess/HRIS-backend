package com.hris.analytics.service;

import com.hris.analytics.dto.*;
import com.hris.analytics.entity.ApprovalBottleneckSnapshot;
import com.hris.analytics.entity.HeadcountMetricsSnapshot;
import com.hris.analytics.entity.LeaveDistributionSnapshot;
import com.hris.analytics.entity.LeaveMetricsSnapshot;
import com.hris.analytics.entity.ProjectAbsenceFact;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.repository.*;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.organisation.entity.Project;
import com.hris.organisation.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final LeaveMetricsSnapshotRepository leaveMetricsSnapshotRepository;
    private final HeadcountMetricsSnapshotRepository headcountMetricsSnapshotRepository;
    private final LeaveDistributionSnapshotRepository leaveDistributionSnapshotRepository;
    private final ApprovalBottleneckSnapshotRepository approvalBottleneckSnapshotRepository;
    private final ProjectAbsenceFactRepository projectAbsenceFactRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public LeaveMetricsSnapshotDto getLeaveMetrics(LocalDate date, AnalyticsScopeType scopeType, UUID scopeId) {
        LeaveMetricsSnapshot snapshot = leaveMetricsSnapshotRepository
            .findBySnapshotDateAndScopeTypeAndScopeId(date, scopeType, scopeId)
            .orElse(null);
        if (snapshot == null && scopeId == null && scopeType == AnalyticsScopeType.GLOBAL) {
            snapshot = leaveMetricsSnapshotRepository.findBySnapshotDateAndScopeType(date, scopeType)
                .stream().findFirst().orElse(null);
        }
        if (snapshot == null) {
            return new LeaveMetricsSnapshotDto(0, 0, 0, 0, BigDecimal.ZERO);
        }
        return new LeaveMetricsSnapshotDto(
            snapshot.getTotalRequests(),
            snapshot.getApprovedCount(),
            snapshot.getRejectedCount(),
            snapshot.getPendingCount(),
            snapshot.getAverageProcessingDays()
        );
    }

    @Transactional(readOnly = true)
    public HeadcountMetricsSnapshotDto getHeadcountMetrics(LocalDate date, AnalyticsScopeType scopeType, UUID scopeId) {
        HeadcountMetricsSnapshot snapshot = headcountMetricsSnapshotRepository
            .findBySnapshotDateAndScopeTypeAndScopeId(date, scopeType, scopeId)
            .orElse(null);
        if (snapshot == null) {
            return new HeadcountMetricsSnapshotDto(0, 0, 0, 0);
        }
        return new HeadcountMetricsSnapshotDto(
            snapshot.getTotalEmployees(),
            snapshot.getActiveEmployees(),
            snapshot.getNewHires(),
            snapshot.getTerminatedEmployees()
        );
    }

    @Transactional(readOnly = true)
    public List<LeaveDistributionSnapshotDto> getLeaveDistribution(LocalDate date, AnalyticsScopeType scopeType, UUID scopeId) {
        Map<UUID, LeaveType> leaveTypes = leaveTypeRepository.findAll().stream()
            .collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        return leaveDistributionSnapshotRepository.findBySnapshotDateAndScopeTypeAndScopeId(date, scopeType, scopeId).stream()
            .map(snapshot -> {
                LeaveType leaveType = leaveTypes.get(snapshot.getLeaveTypeId());
                return new LeaveDistributionSnapshotDto(
                    snapshot.getLeaveTypeId(),
                    leaveType != null ? leaveType.getCode() : null,
                    leaveType != null ? leaveType.getName() : null,
                    snapshot.getRequestCount(),
                    snapshot.getTotalDays()
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalBottleneckSnapshotDto> getApprovalBottlenecks(LocalDate date, AnalyticsScopeType scopeType, UUID scopeId) {
        return approvalBottleneckSnapshotRepository.findBySnapshotDateAndScopeTypeAndScopeId(date, scopeType, scopeId).stream()
            .map(this::toApprovalBottleneckDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectAbsenceFactDto> getProjectAbsence(LocalDate date, AnalyticsScopeType scopeType, UUID scopeId) {
        Map<UUID, Project> projects = projectRepository.findAll().stream()
            .collect(Collectors.toMap(Project::getId, Function.identity()));

        return projectAbsenceFactRepository.findBySnapshotDate(date).stream()
            .filter(fact -> matchesScope(fact, scopeType, scopeId))
            .map(fact -> new ProjectAbsenceFactDto(
                fact.getProjectId(),
                projects.containsKey(fact.getProjectId()) ? projects.get(fact.getProjectId()).getName() : null,
                fact.getTeamId(),
                fact.getAbsentEmployees(),
                fact.getAbsenceDays(),
                fact.getAffectedMembers(),
                fact.getEstimatedDelayDays(),
                fact.getRiskLevel()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveMetricsTimeseriesPointDto> getLeaveMetricsTimeseries(
            LocalDate from,
            LocalDate to,
            AnalyticsScopeType scopeType,
            UUID scopeId) {
        LocalDate normalizedFrom = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate normalizedTo = to != null ? to : LocalDate.now();
        if (normalizedTo.isBefore(normalizedFrom)) {
            normalizedTo = normalizedFrom;
        }

        return findLeaveMetricsSnapshots(normalizedFrom, normalizedTo, scopeType, scopeId).stream()
            .map(snapshot -> new LeaveMetricsTimeseriesPointDto(
                snapshot.getSnapshotDate(),
                snapshot.getTotalRequests(),
                snapshot.getApprovedCount(),
                snapshot.getRejectedCount(),
                snapshot.getPendingCount(),
                snapshot.getAverageProcessingDays()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<HeadcountMetricsTimeseriesPointDto> getHeadcountMetricsTimeseries(
            LocalDate from,
            LocalDate to,
            AnalyticsScopeType scopeType,
            UUID scopeId) {
        LocalDate normalizedFrom = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate normalizedTo = to != null ? to : LocalDate.now();
        if (normalizedTo.isBefore(normalizedFrom)) {
            normalizedTo = normalizedFrom;
        }

        return findHeadcountSnapshots(normalizedFrom, normalizedTo, scopeType, scopeId).stream()
            .map(snapshot -> new HeadcountMetricsTimeseriesPointDto(
                snapshot.getSnapshotDate(),
                snapshot.getTotalEmployees(),
                snapshot.getActiveEmployees(),
                snapshot.getNewHires(),
                snapshot.getTerminatedEmployees()
            ))
            .toList();
    }

    private boolean matchesScope(ProjectAbsenceFact fact, AnalyticsScopeType scopeType, UUID scopeId) {
        if (scopeType == AnalyticsScopeType.GLOBAL) {
            return true;
        }
        if (scopeType == AnalyticsScopeType.PROJECT) {
            return Objects.equals(scopeId, fact.getProjectId());
        }
        if (scopeType == AnalyticsScopeType.TEAM) {
            return Objects.equals(scopeId, fact.getTeamId());
        }
        return true;
    }

    private ApprovalBottleneckSnapshotDto toApprovalBottleneckDto(ApprovalBottleneckSnapshot snapshot) {
        return new ApprovalBottleneckSnapshotDto(
            snapshot.getSourceType(),
            snapshot.getApproverLevel(),
            snapshot.getPendingCount(),
            snapshot.getAverageDecisionDays(),
            snapshot.getRejectionRate()
        );
    }

    private List<LeaveMetricsSnapshot> findLeaveMetricsSnapshots(
            LocalDate from,
            LocalDate to,
            AnalyticsScopeType scopeType,
            UUID scopeId) {
        if (scopeType == AnalyticsScopeType.GLOBAL && scopeId == null) {
            return leaveMetricsSnapshotRepository.findBySnapshotDateBetweenAndScopeTypeOrderBySnapshotDateAsc(
                from, to, scopeType);
        }
        return leaveMetricsSnapshotRepository.findBySnapshotDateBetweenAndScopeTypeAndScopeIdOrderBySnapshotDateAsc(
            from, to, scopeType, scopeId);
    }

    private List<HeadcountMetricsSnapshot> findHeadcountSnapshots(
            LocalDate from,
            LocalDate to,
            AnalyticsScopeType scopeType,
            UUID scopeId) {
        if (scopeType == AnalyticsScopeType.GLOBAL && scopeId == null) {
            return headcountMetricsSnapshotRepository.findBySnapshotDateBetweenAndScopeTypeOrderBySnapshotDateAsc(
                from, to, scopeType);
        }
        return headcountMetricsSnapshotRepository.findBySnapshotDateBetweenAndScopeTypeAndScopeIdOrderBySnapshotDateAsc(
            from, to, scopeType, scopeId);
    }
}
