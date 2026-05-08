package com.hris.leave.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.ApprovalRouteResolver;
import com.hris.approval.service.ApprovalRouter;
import com.hris.approval.service.ApprovalStepFactory;
import com.hris.approval.service.EmployeeHierarchyResolver;
import com.hris.approval.service.ProjectAssignmentHierarchyResolver;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.leave.dto.CreateLeaveRequestDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.entity.Team;
import com.hris.organisation.entity.TeamProjectLink;
import com.hris.organisation.entity.WorkSchedule;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.TeamProjectLinkRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.organisation.repository.WorkScheduleRepository;
import com.hris.organisation.service.WorkScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    LeaveRequestService.class,
    ApprovalRouter.class,
    ApprovalRouteResolver.class,
    ApprovalStepFactory.class,
    EmployeeHierarchyResolver.class,
    ProjectAssignmentHierarchyResolver.class,
    LeaveApprovalRouteIntegrationTest.Config.class
})
@DisplayName("LeaveApprovalRoute Integration Tests")
class LeaveApprovalRouteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("hris_test")
        .withUsername("hris_user")
        .withPassword("hris_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @MockBean
    private WorkScheduleService workScheduleService;

    @MockBean
    private TransactionalNotificationPublisher notificationPublisher;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private LeaveAttachmentService leaveAttachmentService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectDepartmentRepository projectDepartmentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamProjectLinkRepository teamProjectLinkRepository;

    @Autowired
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private ApprovalStepRepository approvalStepRepository;

    @Autowired
    private com.hris.auth.repository.UserRepository userRepository;

    @Test
    @DisplayName("create leave request persists department and project approval chain in order")
    void createLeaveRequestPersistsDepartmentAndProjectApprovalChainInOrder() {
        WorkSchedule schedule = workScheduleRepository.save(WorkSchedule.builder()
            .name("Standard")
            .workingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
            .hoursPerDay(8)
            .build());

        Department engineering = departmentRepository.save(Department.builder()
            .name("Engineering")
            .code("ENG")
            .isActive(true)
            .build());
        Department delivery = departmentRepository.save(Department.builder()
            .name("Delivery")
            .code("DLV")
            .isActive(true)
            .build());

        User departmentSupervisorUser = saveUser("dept-supervisor");
        User projectLeaderUser = saveUser("project-leader");
        User projectManagerUser = saveUser("project-manager");
        User requesterUser = saveUser("requester");

        Employee departmentSupervisor = employeeRepository.save(Employee.builder()
            .userId(departmentSupervisorUser.getId())
            .employeeCode("EMP-DEPT-1")
            .hireDate(LocalDate.of(2020, 1, 1))
            .jobTitle("Department Supervisor")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(engineering.getId())
            .workScheduleId(schedule.getId())
            .build());

        Employee projectManager = employeeRepository.save(Employee.builder()
            .userId(projectManagerUser.getId())
            .employeeCode("EMP-PM-1")
            .hireDate(LocalDate.of(2019, 1, 1))
            .jobTitle("Project Manager")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(delivery.getId())
            .workScheduleId(schedule.getId())
            .build());

        Employee projectLeader = employeeRepository.save(Employee.builder()
            .userId(projectLeaderUser.getId())
            .employeeCode("EMP-LEAD-1")
            .hireDate(LocalDate.of(2021, 1, 1))
            .jobTitle("Team Leader")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(engineering.getId())
            .workScheduleId(schedule.getId())
            .build());

        Employee requester = employeeRepository.save(Employee.builder()
            .userId(requesterUser.getId())
            .employeeCode("EMP-REQ-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .jobTitle("Developer")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(engineering.getId())
            .supervisorEmployeeId(departmentSupervisor.getId())
            .workScheduleId(schedule.getId())
            .build());

        engineering.setHeadEmployeeId(departmentSupervisor.getId());
        departmentRepository.save(engineering);

        Project project = projectRepository.save(Project.builder()
            .name("Atlas")
            .code("ATLAS")
            .status(ProjectStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 1, 1))
            .projectManagerEmployeeId(projectManager.getId())
            .build());

        projectDepartmentRepository.save(ProjectDepartment.builder()
            .projectId(project.getId())
            .departmentId(engineering.getId())
            .isLead(true)
            .build());

        Team team = teamRepository.save(Team.builder()
            .id(UUID.randomUUID())
            .code("ATLAS_BACKEND")
            .departmentId(engineering.getId())
            .name("Backend Squad")
            .supervisorEmployeeId(projectLeader.getId())
            .isActive(true)
            .build());
        teamProjectLinkRepository.save(TeamProjectLink.builder()
            .teamId(team.getId())
            .projectId(project.getId())
            .isActive(true)
            .build());

        projectAssignmentRepository.save(ProjectAssignment.builder()
            .employeeId(requester.getId())
            .projectId(project.getId())
            .teamId(team.getId())
            .supervisorId(projectLeader.getId())
            .assignmentRole(ProjectRole.MEMBER)
            .startDate(LocalDate.of(2026, 1, 1))
            .isActive(true)
            .build());
        projectAssignmentRepository.save(ProjectAssignment.builder()
            .employeeId(projectLeader.getId())
            .projectId(project.getId())
            .teamId(team.getId())
            .supervisorId(projectManager.getId())
            .assignmentRole(ProjectRole.MANAGER)
            .startDate(LocalDate.of(2026, 1, 1))
            .isActive(true)
            .build());

        LeaveType leaveType = leaveTypeRepository.save(LeaveType.builder()
            .code("ANNUAL")
            .name("Annual Leave")
            .isPaid(true)
            .requiresJustification(false)
            .isActive(true)
            .build());

        leaveBalanceRepository.save(LeaveBalance.builder()
            .employeeId(requester.getId())
            .leaveTypeId(leaveType.getId())
            .year(2026)
            .totalDays(20)
            .usedDays(0)
            .pendingDays(0)
            .carryOverDays(0)
            .build());

        when(workScheduleService.computeWorkingDays(any(), any(), eq(schedule.getId()))).thenReturn(3);

        LeaveRequest leaveRequest = leaveRequestService.create(new CreateLeaveRequestDto(
            leaveType.getId(),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 5),
            UrgencyLevel.NORMAL,
            "Vacation"
        ), requesterUser.getId());

        assertThat(leaveRequest.getStatus()).isEqualTo(LeaveStatus.IN_APPROVAL);
        assertThat(leaveRequestRepository.findById(leaveRequest.getId())).isPresent();

        ApprovalWorkflow workflow = approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", leaveRequest.getId())
            .orElseThrow();
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);

        List<ApprovalStep> steps = approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId());
        assertThat(steps).hasSize(3);
        assertThat(steps).extracting(ApprovalStep::getApproverId)
            .containsExactly(
                departmentSupervisorUser.getId(),
                projectLeaderUser.getId(),
                projectManagerUser.getId()
            );
        assertThat(steps.get(0).getRoutingSnapshot()).contains("N_PLUS_1");
        assertThat(steps.get(1).getRoutingSnapshot()).contains("PROJECT_N_PLUS_1");
        assertThat(steps.get(2).getRoutingSnapshot()).contains("PROJECT_N_PLUS_2");
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.builder()
            .keycloakId("kc-" + prefix + "-" + UUID.randomUUID())
            .email(prefix + "@example.com")
            .firstName(prefix)
            .lastName("User")
            .localePreference("en")
            .isActive(true)
            .build());
    }

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
