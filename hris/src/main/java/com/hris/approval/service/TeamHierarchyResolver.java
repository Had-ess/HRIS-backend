package com.hris.approval.service;

import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamHierarchyResolver {

    private final TeamHierarchyRelationRepository relationRepository;

    public RouteCandidateList resolveAboveRequester(UUID teamId, UUID requesterEmployeeId, LocalDate effectiveDate) {
        List<TeamHierarchyRelation> relations = relationRepository
            .findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE)
            .stream()
            .filter(relation -> isEffectiveOn(relation, effectiveDate))
            .toList();

        Map<UUID, UUID> parentByCollaborator = new LinkedHashMap<>();
        for (TeamHierarchyRelation relation : relations) {
            parentByCollaborator.put(relation.getCollaboratorEmployeeId(), relation.getResponsibleEmployeeId());
        }

        List<RouteCandidate> validators = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        UUID current = parentByCollaborator.get(requesterEmployeeId);
        int level = 1;
        while (current != null && visited.add(current)) {
            if (!current.equals(requesterEmployeeId)) {
                validators.add(new RouteCandidate(current, level, parentByCollaborator.get(current)));
            }
            current = parentByCollaborator.get(current);
            level++;
        }

        return new RouteCandidateList(List.copyOf(validators), validators.isEmpty());
    }

    private boolean isEffectiveOn(TeamHierarchyRelation relation, LocalDate date) {
        return !relation.getStartDate().isAfter(date)
            && (relation.getEndDate() == null || !relation.getEndDate().isBefore(date));
    }

    public record RouteCandidateList(List<RouteCandidate> candidates, boolean noValidator) {
        public List<UUID> validatorEmployeeIds() {
            return candidates.stream().map(RouteCandidate::employeeId).toList();
        }
    }

    public record RouteCandidate(UUID employeeId, int level, UUID directResponsibleEmployeeId) {
    }
}
