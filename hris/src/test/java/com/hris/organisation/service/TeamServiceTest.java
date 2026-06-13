package com.hris.organisation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.organisation.dto.TeamCreateDto;
import com.hris.organisation.dto.TeamDto;
import com.hris.organisation.dto.TeamUpdateDto;
import com.hris.organisation.entity.Team;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.TeamProjectLinkRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    @Mock private TeamProjectLinkRepository teamProjectLinkRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private TeamService service;

    private final UUID departmentId = UUID.randomUUID();
    private final UUID supervisorId = UUID.randomUUID();
    private final UUID supervisorUserId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(
            Department.builder().id(departmentId).name("Engineering").code("ENG").isActive(true).build()));
        lenient().when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(
            Employee.builder().id(supervisorId).userId(supervisorUserId).employeeCode("SUP-1")
                .status(EmployeeStatus.ACTIVE).departmentId(departmentId).build()));
        lenient().when(userRepository.findById(supervisorUserId)).thenReturn(Optional.of(
            User.builder().id(supervisorUserId).firstName("Sam").lastName("Lead").build()));
        lenient().when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(teamHierarchyRelationRepository.save(any(TeamHierarchyRelation.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("standing teams: a team can be created without a project")
    void createWithoutProjectSucceeds() {
        when(teamRepository.existsByCodeIgnoreCase("CORE")).thenReturn(false);

        TeamDto result = service.create(
            new TeamCreateDto("CORE", "Core Team", departmentId, null, supervisorId), actorId);

        assertThat(result.projectId()).isNull();
        assertThat(result.projectName()).isNull();
        assertThat(result.supervisorEmployeeId()).isEqualTo(supervisorId);
    }

    @Test
    @DisplayName("create seeds the chain-head hierarchy relation from the supervisor")
    void createSeedsChainHeadRelation() {
        when(teamRepository.existsByCodeIgnoreCase("CORE")).thenReturn(false);

        service.create(new TeamCreateDto("CORE", "Core Team", departmentId, null, supervisorId), actorId);

        ArgumentCaptor<TeamHierarchyRelation> captor = ArgumentCaptor.forClass(TeamHierarchyRelation.class);
        verify(teamHierarchyRelationRepository).save(captor.capture());
        assertThat(captor.getValue().getCollaboratorEmployeeId()).isEqualTo(supervisorId);
        assertThat(captor.getValue().getResponsibleEmployeeId()).isNull();
    }

    @Test
    @DisplayName("supervisor change re-points the active chain head")
    void supervisorChangeRepointsChainHead() {
        UUID teamId = UUID.randomUUID();
        UUID newSupervisorId = UUID.randomUUID();
        Team team = Team.builder()
            .id(teamId).code("CORE").name("Core Team")
            .departmentId(departmentId).supervisorEmployeeId(supervisorId).isActive(true)
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.existsByCodeIgnoreCaseAndIdNot("CORE", teamId)).thenReturn(false);
        when(employeeRepository.findById(newSupervisorId)).thenReturn(Optional.of(
            Employee.builder().id(newSupervisorId).userId(supervisorUserId).employeeCode("SUP-2")
                .status(EmployeeStatus.ACTIVE).departmentId(departmentId).build()));

        TeamHierarchyRelation oldHead = TeamHierarchyRelation.builder()
            .id(UUID.randomUUID()).teamId(teamId)
            .collaboratorEmployeeId(supervisorId).responsibleEmployeeId(null)
            .status(TeamHierarchyStatus.ACTIVE).startDate(LocalDate.now().minusMonths(2))
            .build();
        when(teamHierarchyRelationRepository
            .findByTeamIdAndStatusOrderByStartDateAscCollaboratorEmployeeIdAsc(teamId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(oldHead));

        service.update(teamId, new TeamUpdateDto(null, null, null, null, null, newSupervisorId, null), actorId);

        // new supervisor got a fresh chain-head relation, old head now reports to them
        ArgumentCaptor<TeamHierarchyRelation> captor = ArgumentCaptor.forClass(TeamHierarchyRelation.class);
        verify(teamHierarchyRelationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        TeamHierarchyRelation created = captor.getAllValues().get(0);
        assertThat(created.getCollaboratorEmployeeId()).isEqualTo(newSupervisorId);
        assertThat(created.getResponsibleEmployeeId()).isNull();
        assertThat(oldHead.getResponsibleEmployeeId()).isEqualTo(newSupervisorId);
    }

    @Test
    @DisplayName("clearProject detaches the team from its project")
    void clearProjectDetaches() {
        UUID teamId = UUID.randomUUID();
        Team team = Team.builder()
            .id(teamId).code("CORE").name("Core Team")
            .departmentId(departmentId).projectId(UUID.randomUUID())
            .supervisorEmployeeId(supervisorId).isActive(true)
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.existsByCodeIgnoreCaseAndIdNot("CORE", teamId)).thenReturn(false);

        TeamDto result = service.update(teamId,
            new TeamUpdateDto(null, null, null, null, true, null, null), actorId);

        assertThat(team.getProjectId()).isNull();
        assertThat(result.projectId()).isNull();
    }
}
