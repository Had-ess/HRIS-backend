package com.hris.organisation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DuplicateProjectDepartmentAssignmentException;
import com.hris.common.exception.InvalidProjectAssignmentException;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectDepartmentAssignDto;
import com.hris.organisation.dto.ProjectDepartmentResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.mapper.ProjectMapper;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private ProjectDepartmentRepository projectDepartmentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProjectService projectService;

    private UUID projectId;
    private UUID employeeId;
    private UUID supervisorId;
    private Project project;
    private Employee employee;
    private Employee supervisor;
    private UUID departmentId;
    private Department department;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();
        departmentId = UUID.randomUUID();

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
            .departmentId(UUID.randomUUID())
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

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto))
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

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto))
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
        when(projectAssignmentRepository.countOverlappingActiveAssignments(
            employeeId, projectId, dto.startDate(), dto.endDate())).thenReturn(1L);

        assertThatThrownBy(() -> projectService.assignEmployee(projectId, dto))
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
        when(projectMapper.toAssignmentDto(any(ProjectAssignment.class))).thenAnswer(invocation -> {
            ProjectAssignment assignment = invocation.getArgument(0);
            return new ProjectAssignmentResponseDto(
                assignment.getId(),
                assignment.getEmployeeId(),
                assignment.getProjectId(),
                assignment.getSupervisorId(),
                assignment.getAssignmentRole(),
                assignment.getStartDate(),
                assignment.getEndDate(),
                assignment.isActive());
        });

        ProjectAssignmentResponseDto response = projectService.assignEmployee(projectId, dto);

        assertThat(response.id()).isEqualTo(assignmentId);
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.supervisorId()).isEqualTo(supervisorId);
        verify(projectAssignmentRepository).countOverlappingActiveAssignments(
            employeeId, projectId, dto.startDate(), dto.endDate());
        verify(projectAssignmentRepository).save(any(ProjectAssignment.class));
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

        ProjectDepartmentResponseDto result = projectService.assignDepartment(projectId, dto);

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

        assertThatThrownBy(() -> projectService.assignDepartment(projectId, dto))
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

        projectService.removeDepartment(projectId, departmentId);

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

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectDepartmentRepository.findByProjectId(projectId)).thenReturn(List.of(link));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        List<ProjectDepartmentResponseDto> result = projectService.getDepartments(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).departmentId()).isEqualTo(departmentId);
        assertThat(result.get(0).name()).isEqualTo("Engineering");
        assertThat(result.get(0).isLead()).isTrue();
    }

    private void stubProjectAndEmployees(Employee assignmentEmployee, Employee assignmentSupervisor) {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(employeeRepository.findByIdForUpdate(eq(employeeId))).thenReturn(Optional.of(assignmentEmployee));
        when(employeeRepository.findById(eq(supervisorId))).thenReturn(Optional.of(assignmentSupervisor));
    }
}
