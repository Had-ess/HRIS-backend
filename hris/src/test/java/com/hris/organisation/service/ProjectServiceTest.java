package com.hris.organisation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.Team;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.mapper.ProjectMapper;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.TeamProjectLinkRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private ProjectDepartmentRepository projectDepartmentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamProjectLinkRepository teamProjectLinkRepository;
    @Mock private TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private ProjectMapper projectMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ProjectService service;

    @Test
    @DisplayName("hard delete detaches teams instead of deleting them (standing teams)")
    void hardDeleteDetachesTeams() {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder()
            .id(projectId).name("Atlas").code("ATL")
            .status(ProjectStatus.CANCELLED)
            .build();
        Team team = Team.builder()
            .id(UUID.randomUUID()).code("ATL_CORE").name("Core")
            .departmentId(UUID.randomUUID()).projectId(projectId)
            .supervisorEmployeeId(UUID.randomUUID()).isActive(false)
            .build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectAssignmentRepository.existsByProjectIdAndIsActiveTrue(projectId)).thenReturn(false);
        when(teamRepository.findByProjectId(projectId)).thenReturn(List.of(team));

        service.hardDelete(projectId, actorId);

        assertThat(team.getProjectId()).isNull();
        verify(teamRepository).saveAll(List.of(team));
        verify(teamRepository, never()).deleteAllById(any());
        verify(teamHierarchyRelationRepository, never()).deleteByTeamIdIn(any());
        verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("team hierarchy seeding: leader is chain head, members report to their in-team spine supervisor or the leader")
    void seedTeamHierarchyShapes() {
        UUID teamId = UUID.randomUUID();
        Team team = Team.builder()
            .id(teamId).code("T").name("T")
            .departmentId(UUID.randomUUID()).supervisorEmployeeId(UUID.randomUUID()).isActive(true)
            .build();
        Employee leader = Employee.builder().id(UUID.randomUUID()).build();
        // memberA's spine supervisor is the leader (in team) → reports to leader
        Employee memberA = Employee.builder().id(UUID.randomUUID())
            .supervisorEmployeeId(leader.getId()).build();
        // memberB's spine supervisor is memberA (in team) → reports to memberA
        Employee memberB = Employee.builder().id(UUID.randomUUID())
            .supervisorEmployeeId(memberA.getId()).build();
        // memberC's spine supervisor is outside the team → falls back to leader
        Employee memberC = Employee.builder().id(UUID.randomUUID())
            .supervisorEmployeeId(UUID.randomUUID()).build();

        service.seedTeamHierarchy(team, leader, List.of(memberA, memberB, memberC), LocalDate.now());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TeamHierarchyRelation>> captor =
            ArgumentCaptor.forClass((Class) List.class);
        verify(teamHierarchyRelationRepository).saveAll(captor.capture());
        List<TeamHierarchyRelation> relations = captor.getValue();

        assertThat(relations).hasSize(4);
        assertThat(relations)
            .filteredOn(r -> r.getCollaboratorEmployeeId().equals(leader.getId()))
            .singleElement()
            .satisfies(r -> assertThat(r.getResponsibleEmployeeId()).isNull());
        assertThat(relations)
            .filteredOn(r -> r.getCollaboratorEmployeeId().equals(memberA.getId()))
            .singleElement()
            .satisfies(r -> assertThat(r.getResponsibleEmployeeId()).isEqualTo(leader.getId()));
        assertThat(relations)
            .filteredOn(r -> r.getCollaboratorEmployeeId().equals(memberB.getId()))
            .singleElement()
            .satisfies(r -> assertThat(r.getResponsibleEmployeeId()).isEqualTo(memberA.getId()));
        assertThat(relations)
            .filteredOn(r -> r.getCollaboratorEmployeeId().equals(memberC.getId()))
            .singleElement()
            .satisfies(r -> assertThat(r.getResponsibleEmployeeId()).isEqualTo(leader.getId()));
    }
}
