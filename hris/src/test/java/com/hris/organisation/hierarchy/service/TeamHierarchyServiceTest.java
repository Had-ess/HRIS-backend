package com.hris.organisation.hierarchy.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TeamHierarchyService Unit Tests")
class TeamHierarchyServiceTest {

    @Mock private TeamHierarchyRelationRepository relationRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TeamHierarchyService service;
    private UUID teamId;
    private UUID actorId;
    private UUID headEmployeeId;
    private UUID managerEmployeeId;
    private UUID collaboratorEmployeeId;

    @BeforeEach
    void setUp() {
        service = new TeamHierarchyService(
            relationRepository,
            teamRepository,
            employeeRepository,
            userRepository,
            auditLogService,
            eventPublisher
        );

        teamId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        headEmployeeId = UUID.randomUUID();
        managerEmployeeId = UUID.randomUUID();
        collaboratorEmployeeId = UUID.randomUUID();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(
            Team.builder()
                .id(teamId)
                .code("ENG_PLATFORM")
                .name("Engineering Platform")
                .departmentId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .supervisorEmployeeId(headEmployeeId)
                .isActive(true)
                .build()
        ));

        Employee headEmployee = employee(headEmployeeId, UUID.fromString("10000000-0000-0000-0000-000000000001"), "E001", "alice@hris.local", "Alice", "Head");
        Employee managerEmployee = employee(managerEmployeeId, UUID.fromString("10000000-0000-0000-0000-000000000002"), "E002", "bob@hris.local", "Bob", "Manager");
        Employee collaboratorEmployee = employee(collaboratorEmployeeId, UUID.fromString("10000000-0000-0000-0000-000000000003"), "E003", "cara@hris.local", "Cara", "Collaborator");

        when(employeeRepository.findById(headEmployeeId)).thenReturn(Optional.of(headEmployee));
        when(employeeRepository.findById(managerEmployeeId)).thenReturn(Optional.of(managerEmployee));
        when(employeeRepository.findById(collaboratorEmployeeId)).thenReturn(Optional.of(collaboratorEmployee));
    }

    @Test
    @DisplayName("create rejects self management")
    void createRejectsSelfManagement() {
        TeamHierarchyMutationDto dto = new TeamHierarchyMutationDto(
            teamId,
            collaboratorEmployeeId,
            collaboratorEmployeeId,
            LocalDate.of(2026, 1, 1),
            null
        );

        assertThatThrownBy(() -> service.create(dto, actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Responsible employee cannot be the same as collaborator");

        verify(relationRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects cyclic hierarchy")
    void createRejectsCycle() {
        TeamHierarchyRelation manager = relation(UUID.randomUUID(), teamId, collaboratorEmployeeId, managerEmployeeId, LocalDate.of(2026, 1, 1), null);

        TeamHierarchyMutationDto dto = new TeamHierarchyMutationDto(
            teamId,
            collaboratorEmployeeId,
            managerEmployeeId,
            LocalDate.of(2026, 1, 1),
            null
        );
        when(relationRepository.findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(
                manager
            ));

        assertThatThrownBy(() -> service.create(dto, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Team hierarchy cannot contain cycles");
    }

    @Test
    @DisplayName("create rejects duplicate active collaborator relation for overlapping period")
    void createRejectsDuplicateActiveCollaboratorRelationForOverlappingPeriod() {
        when(relationRepository.findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(
                relation(UUID.randomUUID(), teamId, headEmployeeId, collaboratorEmployeeId, LocalDate.of(2026, 1, 1), null)
            ));

        TeamHierarchyMutationDto dto = new TeamHierarchyMutationDto(
            teamId,
            collaboratorEmployeeId,
            headEmployeeId,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> service.create(dto, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("An active hierarchy relation already exists for this collaborator and team during the selected period");
    }

    @Test
    @DisplayName("list computes levels, subordinate counts, roles, and hierarchy status")
    void listComputesLevelsSubordinateCountsRolesAndStatus() {
        UUID leafEmployeeId = UUID.randomUUID();
        Employee leafEmployee = employee(
            leafEmployeeId,
            UUID.fromString("10000000-0000-0000-0000-000000000004"),
            "E004",
            "dave@hris.local",
            "Dave",
            "Leaf"
        );
        when(employeeRepository.findById(leafEmployeeId)).thenReturn(Optional.of(leafEmployee));

        TeamHierarchyRelation head = relation(UUID.randomUUID(), teamId, null, headEmployeeId, LocalDate.of(2026, 1, 1), null);
        TeamHierarchyRelation manager = relation(UUID.randomUUID(), teamId, headEmployeeId, managerEmployeeId, LocalDate.of(2026, 1, 1), null);
        TeamHierarchyRelation leaf = relation(UUID.randomUUID(), teamId, managerEmployeeId, leafEmployeeId, LocalDate.of(2026, 1, 1), null);

        when(relationRepository.findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(head, manager, leaf));

        List<TeamHierarchyNodeDto> nodes = service.getHierarchy(teamId);

        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).collaboratorEmployeeId()).isEqualTo(headEmployeeId);
        assertThat(nodes.get(0).role()).isEqualTo("RESPONSIBLE");
        assertThat(nodes.get(0).level()).isEqualTo(1);
        assertThat(nodes.get(0).subordinateCount()).isEqualTo(2);
        assertThat(nodes.get(0).hierarchyStatus()).isEqualTo("CHAIN_HEAD");

        assertThat(nodes.get(1).collaboratorEmployeeId()).isEqualTo(managerEmployeeId);
        assertThat(nodes.get(1).directResponsibleEmployeeId()).isEqualTo(headEmployeeId);
        assertThat(nodes.get(1).role()).isEqualTo("RESPONSIBLE");
        assertThat(nodes.get(1).level()).isEqualTo(2);
        assertThat(nodes.get(1).subordinateCount()).isEqualTo(1);
        assertThat(nodes.get(1).hierarchyStatus()).isEqualTo("ATTACHED");

        assertThat(nodes.get(2).collaboratorEmployeeId()).isEqualTo(leafEmployeeId);
        assertThat(nodes.get(2).role()).isEqualTo("COLLABORATOR");
        assertThat(nodes.get(2).level()).isEqualTo(3);
        assertThat(nodes.get(2).subordinateCount()).isZero();
    }

    @Test
    @DisplayName("create rejects missing team")
    void createRejectsMissingTeam() {
        UUID missingTeamId = UUID.randomUUID();
        when(teamRepository.findById(missingTeamId)).thenReturn(Optional.empty());

        TeamHierarchyMutationDto dto = new TeamHierarchyMutationDto(
            missingTeamId,
            collaboratorEmployeeId,
            null,
            LocalDate.of(2026, 1, 1),
            null
        );

        assertThatThrownBy(() -> service.create(dto, actorId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Team not found");
    }

    private Employee employee(UUID employeeId, UUID userId, String employeeCode, String email, String firstName, String lastName) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
            User.builder().id(userId).email(email).firstName(firstName).lastName(lastName).build()
        ));
        return Employee.builder()
            .id(employeeId)
            .userId(userId)
            .employeeCode(employeeCode)
            .status(EmployeeStatus.ACTIVE)
            .build();
    }

    private TeamHierarchyRelation relation(
            UUID id,
            UUID teamId,
            UUID responsibleEmployeeId,
            UUID collaboratorEmployeeId,
            LocalDate startDate,
            LocalDate endDate) {
        return TeamHierarchyRelation.builder()
            .id(id)
            .teamId(teamId)
            .responsibleEmployeeId(responsibleEmployeeId)
            .collaboratorEmployeeId(collaboratorEmployeeId)
            .status(TeamHierarchyStatus.ACTIVE)
            .startDate(startDate)
            .endDate(endDate)
            .build();
    }
}
