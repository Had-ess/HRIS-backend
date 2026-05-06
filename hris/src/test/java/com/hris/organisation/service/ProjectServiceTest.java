package com.hris.organisation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.UserRole;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.RoleRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.DuplicateProjectDepartmentAssignmentException;
import com.hris.common.exception.InvalidProjectAssignmentException;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectAssignmentViewDto;
import com.hris.organisation.dto.ProjectTeamCreateDto;
import com.hris.organisation.dto.ProjectTeamResponseDto;
import com.hris.organisation.dto.ProjectDepartmentAssignDto;
import com.hris.organisation.dto.ProjectDepartmentResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.entity.ProjectTeam;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.mapper.ProjectMapper;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.ProjectTeamRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private ProjectDepartmentRepository projectDepartmentRepository;

    @Mock
    private ProjectTeamRepository projectTeamRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AccessScopeService accessScopeService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TransactionalNotificationPublisher notificationPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectService projectService;

    private UUID projectId;
    private UUID employeeId;
    private UUID supervisorId;
    private Project project;
    private Employee employee;
    private Employee supervisor;
    private Employee teammate;
    private UUID departmentId;
    private UUID secondDepartmentId;
    private Department department;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        secondDepartmentId = UUID.randomUUID();

        project = Project.builder()
            .id(projectId)
            .name("Project Atlas")
            .code("ATLAS")
            .status(ProjectStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 1, 1))
            .build();

        employee = Employee.builder()
            .id(employeeId)
            .userId(UUID.randomUUID())
            .employeeCode("EMP-01")
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Engineer")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(secondDepartmentId)
            .workScheduleId(UUID.randomUUID())
            .build();

        department = Department.builder()
            .id(departmentId)
            .name("Engineering")
            .code("ENG")
            .isActive(true)
            .build();

        supervisor = Employee.builder()
            .id(supervisorId)
            .userId(UUID.randomUUID())
            .employeeCode("SUP-01")
            .hireDate(LocalDate.of(2023, 1, 1))
            .jobTitle("Manager")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(UUID.randomUUID())
            .workScheduleId(UUID.randomUUID())
            .build();

        teammate = Employee.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .employeeCode("EMP-02")
            .hireDate(LocalDate.of(2024, 2, 1))
            .jobTitle("Analyst")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(departmentId)
            .workScheduleId(UUID.randomUUID())
            .build();
    }

    @Test
    @DisplayName("rejects assignment when supervisor and employee are the same")
    void rejectsSelfSupervisorAssignment() {
        ProjectAssignmentCreateDto dto = new ProjectAssignmentCreateDto(
            employeeId, employeeId, ProjectRole.MEMBER,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(employeeRepository.findByIdForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto, ACTOR_ID))
            .isInstanceOf(InvalidProjectAssignmentException.class)
            .hasMessage("Supervisor cannot be the same employee");

        verify(projectAssignmentRepository, never()).save(any(ProjectAssignment.class));
    }

    @Test
    @DisplayName("rejects assignment when start date is after end date")
    void rejectsInvalidDateRange() {
        ProjectAssignmentCreateDto dto = new ProjectAssignmentCreateDto(
            employeeId, supervisorId, ProjectRole.MEMBER,
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1));

        stubProjectAndEmployees(employee, supervisor);

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto, ACTOR_ID))
            .isInstanceOf(InvalidProjectAssignmentException.class)
            .hasMessage("Assignment start date must be before or equal to end date");

        verify(projectAssignmentRepository, never()).save(any(ProjectAssignment.class));
    }

    @Test
    @DisplayName("rejects assignment when overlapping active assignment already exists")
    void rejectsOverlappingAssignment() {
        ProjectAssignmentCreateDto dto = new ProjectAssignmentCreateDto(
            employeeId, supervisorId, ProjectRole.MEMBER,
            LocalDate.of(2026, 7, 1), null);

        stubProjectAndEmployees(employee, supervisor);
        when(projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(
            employeeId, projectId, dto.startDate())).thenReturn(1L);

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto, ACTOR_ID))
            .isInstanceOf(InvalidProjectAssignmentException.class)
            .hasMessage("Overlapping assignment already exists for this employee and project");

        verify(projectAssignmentRepository, never()).save(any(ProjectAssignment.class));
    }

    @Test
    @DisplayName("creates assignment when validation passes")
    void acceptsValidAssignment() {
        ProjectAssignmentCreateDto dto = new ProjectAssignmentCreateDto(
            employeeId, supervisorId, ProjectRole.MANAGER,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID assignmentId = UUID.randomUUID();

        stubProjectAndEmployees(employee, supervisor);
        when(projectAssignmentRepository.countOverlappingActiveAssignments(
            employeeId, projectId, dto.startDate(), dto.endDate())).thenReturn(0L);
        when(projectAssignmentRepository.save(any(ProjectAssignment.class))).thenAnswer(invocation -> {
            ProjectAssignment assignment = invocation.getArgument(0);
            assignment.setId(assignmentId);
            return assignment;
        });
        when(userRepository.findById(employee.getUserId())).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(employee.getUserId())
                .firstName("Employee")
                .lastName("One")
                .email("employee.one@demo.hris.local")
                .localePreference("en")
                .build()
        ));
        when(userRepository.findById(supervisor.getUserId())).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(supervisor.getUserId())
                .firstName("Supervisor")
                .lastName("One")
                .email("supervisor.one@demo.hris.local")
                .localePreference("en")
                .build()
        ));
        when(projectMapper.toAssignmentDto(any(ProjectAssignment.class))).thenAnswer(invocation -> {
            ProjectAssignment assignment = invocation.getArgument(0);
            return new ProjectAssignmentResponseDto(
                assignment.getId(),
                assignment.getEmployeeId(),
                assignment.getProjectId(),
                assignment.getTeamId(),
                assignment.getSupervisorId(),
                assignment.getAssignmentRole(),
                assignment.getStartDate(),
                assignment.getEndDate(),
                assignment.isActive());
        });

        ProjectAssignmentResponseDto response = projectService.assignEmployee(projectId, dto, ACTOR_ID);

        assertThat(response.id()).isEqualTo(assignmentId);
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.supervisorId()).isEqualTo(supervisorId);
        verify(projectAssignmentRepository).countOverlappingActiveAssignments(
            employeeId, projectId, dto.startDate(), dto.endDate());
        verify(projectAssignmentRepository).save(any(ProjectAssignment.class));
        verify(notificationPublisher).publishAfterCommit(argThat(event ->
            event.getEventType() == NotificationEventType.PROJECT_ASSIGNED
                && event.getTargetUserId().equals(employee.getUserId())
        ));
    }

    @Test
    @DisplayName("assign department to project works")
    void assignDepartmentToProjectWorks() {
        ProjectDepartmentAssignDto dto = new ProjectDepartmentAssignDto(departmentId, true);
        UUID linkId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(projectDepartmentRepository.existsByProjectIdAndDepartmentId(projectId, departmentId)).thenReturn(false);
        when(projectDepartmentRepository.save(any(ProjectDepartment.class))).thenAnswer(invocation -> {
            ProjectDepartment link = invocation.getArgument(0);
            link.setId(linkId);
            return link;
        });

        ProjectDepartmentResponseDto result = projectService.assignDepartment(projectId, dto, ACTOR_ID);

        assertThat(result.id()).isEqualTo(linkId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.isLead()).isTrue();
        verify(projectDepartmentRepository).save(any(ProjectDepartment.class));
    }

    @Test
    @DisplayName("duplicate department assignment is rejected")
    void duplicateDepartmentAssignmentIsRejected() {
        ProjectDepartmentAssignDto dto = new ProjectDepartmentAssignDto(departmentId, false);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(projectDepartmentRepository.existsByProjectIdAndDepartmentId(projectId, departmentId)).thenReturn(true);

        assertThatThrownBy(() -> projectService.assignDepartment(projectId, dto, ACTOR_ID))
            .isInstanceOf(DuplicateProjectDepartmentAssignmentException.class)
            .hasMessage("Department is already assigned to this project");
    }

    @Test
    @DisplayName("remove department from project works")
    void removeDepartmentFromProjectWorks() {
        ProjectDepartment link = ProjectDepartment.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .departmentId(departmentId)
            .isLead(false)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectDepartmentRepository.findByProjectIdAndDepartmentId(projectId, departmentId))
            .thenReturn(Optional.of(link));

        projectService.removeDepartment(projectId, departmentId, ACTOR_ID);

        verify(projectDepartmentRepository).delete(link);
    }

    @Test
    @DisplayName("list departments for project returns expected data")
    void listDepartmentsForProjectReturnsExpectedData() {
        ProjectDepartment link = ProjectDepartment.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .departmentId(departmentId)
            .isLead(true)
            .build();
        UserRole administrationRole = UserRole.builder()
            .role(Role.builder().code("ADMINISTRATION").isActive(true).build())
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectDepartmentRepository.findByProjectId(projectId)).thenReturn(List.of(link));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(accessScopeService.getEffectiveRoles(ACTOR_ID)).thenReturn(List.of(administrationRole));
        when(accessScopeService.hasAdministrationOrHrVisibility(List.of(administrationRole))).thenReturn(true);

        List<ProjectDepartmentResponseDto> result = projectService.getDepartments(projectId, ACTOR_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).departmentId()).isEqualTo(departmentId);
        assertThat(result.get(0).name()).isEqualTo("Engineering");
        assertThat(result.get(0).isLead()).isTrue();
    }

    @Test
    @DisplayName("lists active project assignments for scoped readers")
    void listsActiveAssignmentsForScopedReaders() {
        UserRole administrationRole = UserRole.builder()
            .role(Role.builder().code("ADMINISTRATION").isActive(true).build())
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(accessScopeService.getEffectiveRoles(ACTOR_ID)).thenReturn(List.of(administrationRole));
        when(accessScopeService.hasAdministrationOrHrVisibility(List.of(administrationRole))).thenReturn(true);
        when(projectAssignmentRepository.findActiveViewsByProjectId(projectId)).thenReturn(List.of(
            new ProjectAssignmentViewDto(
                UUID.randomUUID(),
                employeeId,
                employee.getUserId(),
                employee.getEmployeeCode(),
                "Employee One",
                projectId,
                null,
                null,
                supervisorId,
                supervisor.getUserId(),
                supervisor.getEmployeeCode(),
                "Supervisor One",
                ProjectRole.MANAGER,
                LocalDate.of(2026, 1, 1),
                null,
                true
            )
        ));

        List<ProjectAssignmentViewDto> result = projectService.getAssignments(projectId, ACTOR_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeeName()).isEqualTo("Employee One");
        assertThat(result.get(0).supervisorName()).isEqualTo("Supervisor One");
    }

    @Test
    @DisplayName("project supervisors can still read projects they participate in")
    void projectSupervisorsCanReadParticipatingProjects() {
        UUID viewerUserId = UUID.randomUUID();
        UUID viewerEmployeeId = UUID.randomUUID();
        UserRole supervisorRole = UserRole.builder()
            .role(Role.builder().code("PROJECT_SUPERVISOR").isActive(true).build())
            .build();
        Employee viewerEmployee = Employee.builder()
            .id(viewerEmployeeId)
            .userId(viewerUserId)
            .employeeCode("SUP-02")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(UUID.randomUUID())
            .workScheduleId(UUID.randomUUID())
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Supervisor")
            .build();

        when(accessScopeService.getEffectiveRoles(viewerUserId)).thenReturn(List.of(supervisorRole));
        when(accessScopeService.hasAdministrationOrHrVisibility(List.of(supervisorRole))).thenReturn(false);
        when(accessScopeService.findEmployee(viewerUserId)).thenReturn(Optional.of(viewerEmployee));
        when(accessScopeService.resolveDepartmentManagerDepartmentId(List.of(supervisorRole), viewerEmployee))
            .thenReturn(Optional.empty());
        when(accessScopeService.hasAnyRole(List.of(supervisorRole), "PROJECT_SUPERVISOR")).thenReturn(true);
        when(projectAssignmentRepository.findActiveProjectIdsByEmployeeId(viewerEmployeeId, LocalDate.now()))
            .thenReturn(List.of(projectId));
        when(projectAssignmentRepository.findActiveProjectIdsBySupervisorId(viewerEmployeeId, LocalDate.now()))
            .thenReturn(List.of());
        when(projectRepository.findProjectIdsByProjectManagerEmployeeId(viewerEmployeeId)).thenReturn(List.of());
        when(projectRepository.findByIdIn(eq(List.of(projectId)), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(project)));

        var page = projectService.getAll(viewerUserId, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("create team creates team and member assignments")
    void createTeamCreatesTeamAndMemberAssignments() {
        UUID teamId = UUID.randomUUID();
        UUID memberOneId = employeeId;
        UUID memberTwoId = teammate.getId();
        ProjectDepartment linkedDepartment = ProjectDepartment.builder()
            .projectId(projectId)
            .departmentId(departmentId)
            .build();
        ProjectDepartment linkedSecondDepartment = ProjectDepartment.builder()
            .projectId(projectId)
            .departmentId(secondDepartmentId)
            .build();
        ProjectTeamCreateDto dto = new ProjectTeamCreateDto(
            "Backend Squad",
            departmentId,
            supervisorId,
            List.of(memberOneId, memberTwoId),
            LocalDate.of(2026, 9, 1),
            null
        );
        Role projectSupervisorRole = Role.builder()
            .id(UUID.randomUUID())
            .code("PROJECT_SUPERVISOR")
            .isActive(true)
            .build();

        supervisor.setDepartmentId(departmentId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectDepartmentRepository.existsByProjectIdAndDepartmentId(projectId, departmentId)).thenReturn(true);
        when(projectDepartmentRepository.findByProjectId(projectId))
            .thenReturn(List.of(linkedDepartment, linkedSecondDepartment));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        when(employeeRepository.findAllById(dto.employeeIds())).thenReturn(List.of(employee, teammate));
        when(projectTeamRepository.save(any(ProjectTeam.class))).thenAnswer(invocation -> {
            ProjectTeam team = invocation.getArgument(0);
            team.setId(teamId);
            return team;
        });
        when(projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(supervisorId, projectId, dto.startDate()))
            .thenReturn(0L);
        when(projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(memberOneId, projectId, dto.startDate()))
            .thenReturn(0L);
        when(projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(memberTwoId, projectId, dto.startDate()))
            .thenReturn(0L);
        when(projectAssignmentRepository.save(any(ProjectAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectAssignmentRepository.countByTeamIdAndIsActiveTrue(teamId)).thenReturn(3L);
        when(userRepository.findById(employee.getUserId())).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(employee.getUserId())
                .firstName("Employee")
                .lastName("One")
                .localePreference("en")
                .build()
        ));
        when(userRepository.findById(teammate.getUserId())).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(teammate.getUserId())
                .firstName("Employee")
                .lastName("Two")
                .localePreference("en")
                .build()
        ));
        when(userRepository.findById(supervisor.getUserId())).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(supervisor.getUserId())
                .firstName("Supervisor")
                .lastName("One")
                .localePreference("en")
                .build()
        ));
        when(roleRepository.findByCode("PROJECT_SUPERVISOR")).thenReturn(Optional.of(projectSupervisorRole));
        when(userRoleRepository.existsByUserIdAndRoleIdAndIsActiveTrue(supervisor.getUserId(), projectSupervisorRole.getId()))
            .thenReturn(false);
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> {
            UserRole userRole = invocation.getArgument(0);
            userRole.setId(UUID.randomUUID());
            return userRole;
        });

        ProjectTeamResponseDto result = projectService.createTeam(projectId, dto, ACTOR_ID);

        assertThat(result.id()).isEqualTo(teamId);
        assertThat(result.memberCount()).isEqualTo(3L);
        verify(projectAssignmentRepository).save(argThat(assignment ->
            assignment.getTeamId().equals(teamId)
                && assignment.getSupervisorId().equals(supervisorId)
                && assignment.getEmployeeId().equals(supervisorId)
                && assignment.getAssignmentRole() == ProjectRole.MANAGER
        ));
        verify(projectAssignmentRepository).save(argThat(assignment ->
            assignment.getTeamId().equals(teamId)
                && assignment.getSupervisorId().equals(supervisorId)
                && assignment.getEmployeeId().equals(memberOneId)
                && assignment.getAssignmentRole() == ProjectRole.MEMBER
        ));
        verify(projectAssignmentRepository).save(argThat(assignment ->
            assignment.getTeamId().equals(teamId)
                && assignment.getSupervisorId().equals(supervisorId)
                && assignment.getEmployeeId().equals(memberTwoId)
                && assignment.getAssignmentRole() == ProjectRole.MEMBER
        ));
        verify(userRoleRepository).save(argThat(userRole ->
            userRole.getUserId().equals(supervisor.getUserId())
                && userRole.getRoleId().equals(projectSupervisorRole.getId())
        ));
    }

    @Test
    @DisplayName("project supervisors can read projects through teams they supervise")
    void projectSupervisorsCanReadProjectsThroughTeamsTheySupervise() {
        UUID viewerUserId = UUID.randomUUID();
        UUID viewerEmployeeId = UUID.randomUUID();
        UserRole supervisorRole = UserRole.builder()
            .role(Role.builder().code("PROJECT_SUPERVISOR").isActive(true).build())
            .build();
        Employee viewerEmployee = Employee.builder()
            .id(viewerEmployeeId)
            .userId(viewerUserId)
            .employeeCode("SUP-02")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(UUID.randomUUID())
            .workScheduleId(UUID.randomUUID())
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Supervisor")
            .build();

        when(accessScopeService.getEffectiveRoles(viewerUserId)).thenReturn(List.of(supervisorRole));
        when(accessScopeService.hasAdministrationOrHrVisibility(List.of(supervisorRole))).thenReturn(false);
        when(accessScopeService.findEmployee(viewerUserId)).thenReturn(Optional.of(viewerEmployee));
        when(accessScopeService.resolveDepartmentManagerDepartmentId(List.of(supervisorRole), viewerEmployee))
            .thenReturn(Optional.empty());
        when(accessScopeService.hasAnyRole(List.of(supervisorRole), "PROJECT_SUPERVISOR")).thenReturn(true);
        when(projectAssignmentRepository.findActiveProjectIdsByEmployeeId(viewerEmployeeId, LocalDate.now()))
            .thenReturn(List.of());
        when(projectAssignmentRepository.findActiveProjectIdsBySupervisorId(viewerEmployeeId, LocalDate.now()))
            .thenReturn(List.of());
        when(projectRepository.findProjectIdsByProjectManagerEmployeeId(viewerEmployeeId)).thenReturn(List.of());
        when(projectTeamRepository.findProjectIdsBySupervisorEmployeeId(viewerEmployeeId))
            .thenReturn(List.of(projectId));
        when(projectRepository.findByIdIn(eq(List.of(projectId)), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(project)));

        var page = projectService.getAll(viewerUserId, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("project managers can read projects they manage")
    void projectManagersCanReadManagedProjects() {
        UUID viewerUserId = UUID.randomUUID();
        UUID viewerEmployeeId = UUID.randomUUID();
        project.setProjectManagerEmployeeId(viewerEmployeeId);
        Employee viewerEmployee = Employee.builder()
            .id(viewerEmployeeId)
            .userId(viewerUserId)
            .employeeCode("PM-01")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(UUID.randomUUID())
            .workScheduleId(UUID.randomUUID())
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Project Manager")
            .build();

        when(accessScopeService.getEffectiveRoles(viewerUserId)).thenReturn(List.of());
        when(accessScopeService.hasAdministrationOrHrVisibility(List.of())).thenReturn(false);
        when(accessScopeService.findEmployee(viewerUserId)).thenReturn(Optional.of(viewerEmployee));
        when(accessScopeService.resolveDepartmentManagerDepartmentId(List.of(), viewerEmployee))
            .thenReturn(Optional.empty());
        when(accessScopeService.hasAnyRole(List.of(), "PROJECT_SUPERVISOR")).thenReturn(false);
        when(projectRepository.findProjectIdsByProjectManagerEmployeeId(viewerEmployeeId)).thenReturn(List.of(projectId));
        when(projectAssignmentRepository.findActiveProjectIdsByEmployeeId(viewerEmployeeId, LocalDate.now()))
            .thenReturn(List.of());
        when(projectRepository.findByIdIn(eq(List.of(projectId)), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(project)));
        when(employeeRepository.findById(viewerEmployeeId)).thenReturn(Optional.of(viewerEmployee));
        when(userRepository.findById(viewerUserId)).thenReturn(Optional.of(
            com.hris.auth.entity.User.builder()
                .id(viewerUserId)
                .firstName("Project")
                .lastName("Manager")
                .localePreference("en")
                .build()
        ));

        var page = projectService.getAll(viewerUserId, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).projectManagerEmployeeId()).isEqualTo(viewerEmployeeId);
        assertThat(page.getContent().get(0).projectManagerName()).isEqualTo("Project Manager");
    }

    private void stubProjectAndEmployees(Employee assignmentEmployee, Employee assignmentSupervisor) {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(employeeRepository.findByIdForUpdate(eq(employeeId))).thenReturn(Optional.of(assignmentEmployee));
        when(employeeRepository.findById(eq(supervisorId))).thenReturn(Optional.of(assignmentSupervisor));
    }
}
