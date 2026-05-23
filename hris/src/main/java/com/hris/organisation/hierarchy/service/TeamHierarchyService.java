package com.hris.organisation.hierarchy.service;

import com.hris.access.enums.StructuralEventType;
import com.hris.access.event.StructuralChangeEvent;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.entity.Team;
import com.hris.organisation.hierarchy.dto.TeamHierarchyMutationDto;
import com.hris.organisation.hierarchy.dto.TeamHierarchyNodeDto;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamHierarchyService {

    private final TeamHierarchyRelationRepository relationRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public List<TeamHierarchyNodeDto> getHierarchy(UUID teamId) {
        getTeam(teamId);
        List<TeamHierarchyRelation> relations = relationRepository
            .findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE)
            .stream()
            .filter(this::isEffectiveToday)
            .toList();

        Map<UUID, TeamHierarchyRelation> relationByCollaborator = relations.stream()
            .collect(Collectors.toMap(TeamHierarchyRelation::getCollaboratorEmployeeId, relation -> relation));
        Map<UUID, List<UUID>> children = buildChildrenMap(relations);
        Map<UUID, Integer> levels = computeLevels(relationByCollaborator);

        return relations.stream()
            .sorted(Comparator
                .comparingInt((TeamHierarchyRelation relation) -> levels.getOrDefault(relation.getCollaboratorEmployeeId(), Integer.MAX_VALUE))
                .thenComparing(relation -> resolveEmployeeName(getEmployee(relation.getCollaboratorEmployeeId()))))
            .map(relation -> toNodeDto(relation, relationByCollaborator, children, levels))
            .toList();
    }

    @Transactional
    public TeamHierarchyNodeDto create(TeamHierarchyMutationDto dto, UUID actorId) {
        validateMutation(dto, null);
        TeamHierarchyRelation relation = TeamHierarchyRelation.builder()
            .teamId(dto.teamId())
            .responsibleEmployeeId(dto.responsibleEmployeeId())
            .collaboratorEmployeeId(dto.collaboratorEmployeeId())
            .status(TeamHierarchyStatus.ACTIVE)
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .build();
        TeamHierarchyRelation saved = relationRepository.save(relation);
        auditLogService.log(actorId, AuditAction.CREATE, "team_hierarchy_relation", saved.getId(), null, snapshot(saved));
        publishDesignationEvent(saved, null, actorId);
        return getNode(saved.getId());
    }

    @Transactional
    public TeamHierarchyNodeDto update(UUID id, TeamHierarchyMutationDto dto, UUID actorId) {
        TeamHierarchyRelation existing = getRelation(id);
        validateMutation(dto, id);

        Map<String, Object> previous = snapshot(existing);
        TeamHierarchyRelation before = TeamHierarchyRelation.builder()
            .teamId(existing.getTeamId())
            .responsibleEmployeeId(existing.getResponsibleEmployeeId())
            .collaboratorEmployeeId(existing.getCollaboratorEmployeeId())
            .status(existing.getStatus())
            .startDate(existing.getStartDate())
            .endDate(existing.getEndDate())
            .build();
        existing.setTeamId(dto.teamId());
        existing.setResponsibleEmployeeId(dto.responsibleEmployeeId());
        existing.setCollaboratorEmployeeId(dto.collaboratorEmployeeId());
        existing.setStartDate(dto.startDate());
        existing.setEndDate(dto.endDate());
        existing.setStatus(TeamHierarchyStatus.ACTIVE);
        TeamHierarchyRelation saved = relationRepository.save(existing);
        auditLogService.log(actorId, AuditAction.UPDATE, "team_hierarchy_relation", saved.getId(), previous, snapshot(saved));
        publishDesignationEvent(saved, before, actorId);
        return getNode(saved.getId());
    }

    @Transactional
    public void endRelation(UUID id, UUID actorId) {
        TeamHierarchyRelation relation = getRelation(id);
        Map<String, Object> previous = snapshot(relation);
        TeamHierarchyRelation before = TeamHierarchyRelation.builder()
            .teamId(relation.getTeamId())
            .responsibleEmployeeId(relation.getResponsibleEmployeeId())
            .collaboratorEmployeeId(relation.getCollaboratorEmployeeId())
            .status(relation.getStatus())
            .startDate(relation.getStartDate())
            .endDate(relation.getEndDate())
            .build();
        relation.setStatus(TeamHierarchyStatus.ENDED);
        if (relation.getEndDate() == null || relation.getEndDate().isAfter(LocalDate.now())) {
            relation.setEndDate(LocalDate.now());
        }
        relationRepository.save(relation);
        auditLogService.log(actorId, AuditAction.UPDATE, "team_hierarchy_relation", relation.getId(), previous, snapshot(relation));
        publishEndedEvent(before, actorId);
    }

    private TeamHierarchyNodeDto getNode(UUID relationId) {
        TeamHierarchyRelation relation = getRelation(relationId);
        return getHierarchy(relation.getTeamId()).stream()
            .filter(node -> node.id().equals(relationId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Team hierarchy relation node could not be resolved"));
    }

    private void validateMutation(TeamHierarchyMutationDto dto, UUID currentId) {
        getTeam(dto.teamId());
        getEmployee(dto.collaboratorEmployeeId());
        if (dto.responsibleEmployeeId() != null) {
            getEmployee(dto.responsibleEmployeeId());
        }
        if (dto.responsibleEmployeeId() != null && dto.responsibleEmployeeId().equals(dto.collaboratorEmployeeId())) {
            throw new IllegalArgumentException("Responsible employee cannot be the same as collaborator");
        }
        if (dto.endDate() != null && dto.startDate().isAfter(dto.endDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        List<TeamHierarchyRelation> activeRelations = relationRepository
            .findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(dto.teamId(), TeamHierarchyStatus.ACTIVE);

        boolean overlappingCollaboratorRelation = activeRelations.stream()
            .filter(relation -> currentId == null || !relation.getId().equals(currentId))
            .filter(relation -> relation.getCollaboratorEmployeeId().equals(dto.collaboratorEmployeeId()))
            .anyMatch(relation -> overlaps(relation.getStartDate(), relation.getEndDate(), dto.startDate(), dto.endDate()));
        if (overlappingCollaboratorRelation) {
            throw new IllegalStateException("An active hierarchy relation already exists for this collaborator and team during the selected period");
        }

        if (dto.responsibleEmployeeId() != null) {
            boolean responsibleHasPresence = activeRelations.stream()
                .filter(relation -> currentId == null || !relation.getId().equals(currentId))
                .filter(relation -> relation.getCollaboratorEmployeeId().equals(dto.responsibleEmployeeId()))
                .anyMatch(relation -> overlaps(relation.getStartDate(), relation.getEndDate(), dto.startDate(), dto.endDate()));
            if (!responsibleHasPresence) {
                throw new IllegalStateException("Responsible employee must already belong to the selected team hierarchy during the selected period");
            }
        }

        ensureNoCycle(dto, currentId, activeRelations);
    }

    private void ensureNoCycle(
            TeamHierarchyMutationDto dto,
            UUID currentId,
            List<TeamHierarchyRelation> activeRelations) {
        if (dto.responsibleEmployeeId() == null) {
            return;
        }
        Map<UUID, UUID> parentByCollaborator = new LinkedHashMap<>();
        for (TeamHierarchyRelation relation : activeRelations) {
            if (currentId != null && relation.getId().equals(currentId)) {
                continue;
            }
            if (!overlaps(relation.getStartDate(), relation.getEndDate(), dto.startDate(), dto.endDate())) {
                continue;
            }
            parentByCollaborator.put(relation.getCollaboratorEmployeeId(), relation.getResponsibleEmployeeId());
        }
        parentByCollaborator.put(dto.collaboratorEmployeeId(), dto.responsibleEmployeeId());

        Set<UUID> visited = new HashSet<>();
        UUID current = dto.responsibleEmployeeId();
        while (current != null && visited.add(current)) {
            if (current.equals(dto.collaboratorEmployeeId())) {
                throw new IllegalStateException("Team hierarchy cannot contain cycles");
            }
            current = parentByCollaborator.get(current);
        }
    }

    private Map<UUID, List<UUID>> buildChildrenMap(List<TeamHierarchyRelation> relations) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (TeamHierarchyRelation relation : relations) {
            if (relation.getResponsibleEmployeeId() == null) {
                continue;
            }
            children.computeIfAbsent(relation.getResponsibleEmployeeId(), ignored -> new ArrayList<>())
                .add(relation.getCollaboratorEmployeeId());
        }
        return children;
    }

    private Map<UUID, Integer> computeLevels(Map<UUID, TeamHierarchyRelation> relationByCollaborator) {
        Map<UUID, Integer> levels = new HashMap<>();
        for (TeamHierarchyRelation relation : relationByCollaborator.values()) {
            computeLevel(relation.getCollaboratorEmployeeId(), relationByCollaborator, levels, new HashSet<>());
        }
        return levels;
    }

    private int computeLevel(
            UUID collaboratorId,
            Map<UUID, TeamHierarchyRelation> relationByCollaborator,
            Map<UUID, Integer> levels,
            Set<UUID> path) {
        if (levels.containsKey(collaboratorId)) {
            return levels.get(collaboratorId);
        }
        if (!path.add(collaboratorId)) {
            throw new IllegalStateException("Team hierarchy cannot contain cycles");
        }
        TeamHierarchyRelation relation = relationByCollaborator.get(collaboratorId);
        int level;
        if (relation == null || relation.getResponsibleEmployeeId() == null) {
            level = 1;
        } else {
            level = computeLevel(relation.getResponsibleEmployeeId(), relationByCollaborator, levels, path) + 1;
        }
        path.remove(collaboratorId);
        levels.put(collaboratorId, level);
        return level;
    }

    private TeamHierarchyNodeDto toNodeDto(
            TeamHierarchyRelation relation,
            Map<UUID, TeamHierarchyRelation> relationByCollaborator,
            Map<UUID, List<UUID>> children,
            Map<UUID, Integer> levels) {
        Employee collaborator = getEmployee(relation.getCollaboratorEmployeeId());
        Employee responsible = relation.getResponsibleEmployeeId() == null
            ? null
            : getEmployee(relation.getResponsibleEmployeeId());
        long subordinateCount = countSubordinates(relation.getCollaboratorEmployeeId(), children);
        String role = subordinateCount > 0 || relation.getResponsibleEmployeeId() == null ? "RESPONSIBLE" : "COLLABORATOR";
        String hierarchyStatus = relation.getResponsibleEmployeeId() == null ? "CHAIN_HEAD" : "ATTACHED";

        return new TeamHierarchyNodeDto(
            relation.getId(),
            relation.getTeamId(),
            collaborator.getId(),
            collaborator.getEmployeeCode(),
            resolveEmployeeName(collaborator),
            responsible != null ? responsible.getId() : null,
            responsible != null ? responsible.getEmployeeCode() : null,
            responsible != null ? resolveEmployeeName(responsible) : null,
            role,
            levels.getOrDefault(relation.getCollaboratorEmployeeId(), 1),
            subordinateCount,
            hierarchyStatus,
            relation.getStatus().name(),
            relation.getStartDate(),
            relation.getEndDate(),
            relation.getCreatedAt(),
            relation.getUpdatedAt()
        );
    }

    private long countSubordinates(UUID collaboratorEmployeeId, Map<UUID, List<UUID>> children) {
        List<UUID> directChildren = children.getOrDefault(collaboratorEmployeeId, List.of());
        long nested = 0;
        for (UUID childId : directChildren) {
            nested += countSubordinates(childId, children);
        }
        return directChildren.size() + nested;
    }

    private boolean overlaps(LocalDate leftStart, LocalDate leftEnd, LocalDate rightStart, LocalDate rightEnd) {
        LocalDate normalizedLeftEnd = leftEnd == null ? LocalDate.MAX : leftEnd;
        LocalDate normalizedRightEnd = rightEnd == null ? LocalDate.MAX : rightEnd;
        return !leftStart.isAfter(normalizedRightEnd) && !rightStart.isAfter(normalizedLeftEnd);
    }

    private boolean isEffectiveToday(TeamHierarchyRelation relation) {
        LocalDate today = LocalDate.now();
        return !relation.getStartDate().isAfter(today)
            && (relation.getEndDate() == null || !relation.getEndDate().isBefore(today));
    }

    private Team getTeam(UUID teamId) {
        return teamRepository.findById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("Team not found"));
    }

    private TeamHierarchyRelation getRelation(UUID relationId) {
        return relationRepository.findById(relationId)
            .orElseThrow(() -> new EntityNotFoundException("Team hierarchy relation not found"));
    }

    private Employee getEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private String resolveEmployeeName(Employee employee) {
        User user = userRepository.findById(employee.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        return name.isBlank() ? employee.getEmployeeCode() : name;
    }

    private Map<String, Object> snapshot(TeamHierarchyRelation relation) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("teamId", relation.getTeamId());
        state.put("responsibleEmployeeId", relation.getResponsibleEmployeeId());
        state.put("collaboratorEmployeeId", relation.getCollaboratorEmployeeId());
        state.put("status", relation.getStatus());
        state.put("startDate", relation.getStartDate());
        state.put("endDate", relation.getEndDate());
        return state;
    }

    private void publishDesignationEvent(TeamHierarchyRelation after, TeamHierarchyRelation before, UUID actorId) {
        boolean isHeadNow = after.getResponsibleEmployeeId() == null
            && after.getStatus() == TeamHierarchyStatus.ACTIVE;
        boolean wasHeadBefore = before != null
            && before.getResponsibleEmployeeId() == null
            && before.getStatus() == TeamHierarchyStatus.ACTIVE
            && Objects.equals(before.getCollaboratorEmployeeId(), after.getCollaboratorEmployeeId());

        if (isHeadNow && !wasHeadBefore) {
            publishEvent(StructuralEventType.TEAM_HEAD_ASSIGNED, after.getCollaboratorEmployeeId(), after.getId(), actorId);
        } else if (!isHeadNow && wasHeadBefore) {
            publishEvent(StructuralEventType.TEAM_HEAD_REMOVED, before.getCollaboratorEmployeeId(), after.getId(), actorId);
        }

        boolean designatedNow = after.getStatus() == TeamHierarchyStatus.ACTIVE && !isHeadNow;
        boolean designatedBefore = before != null
            && before.getStatus() == TeamHierarchyStatus.ACTIVE
            && before.getResponsibleEmployeeId() != null
            && Objects.equals(before.getCollaboratorEmployeeId(), after.getCollaboratorEmployeeId());

        if (designatedNow && !designatedBefore && !isHeadNow) {
            publishEvent(StructuralEventType.APPROVER_DESIGNATED, after.getResponsibleEmployeeId(), after.getId(), actorId);
        }
    }

    private void publishEndedEvent(TeamHierarchyRelation before, UUID actorId) {
        if (before == null) {
            return;
        }
        if (before.getResponsibleEmployeeId() == null) {
            publishEvent(StructuralEventType.TEAM_HEAD_REMOVED, before.getCollaboratorEmployeeId(), before.getTeamId(), actorId);
        } else {
            publishEvent(StructuralEventType.APPROVER_REMOVED, before.getResponsibleEmployeeId(), before.getTeamId(), actorId);
        }
    }

    private void publishEvent(StructuralEventType type, UUID employeeId, UUID refId, UUID actorId) {
        if (employeeId == null) {
            return;
        }
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return;
        }
        applicationEventPublisher.publishEvent(StructuralChangeEvent.of(
            type, employee.getUserId(), refId, actorId));
    }
}
