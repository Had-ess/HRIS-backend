package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.UserRole;
import com.hris.auth.mapper.DepartmentMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DepartmentDeletionNotAllowedException;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProjectDepartmentRepository projectDepartmentRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private AccessScopeService accessScopeService;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DepartmentService departmentService;

    private UUID departmentId;
    private UUID actorId;
    private Department department;

    @BeforeEach
    void setUp() {
        departmentId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        department = Department.builder()
            .id(departmentId)
            .name("Engineering")
            .code("ENG")
            .isActive(true)
            .build();
    }

    @Test
    @DisplayName("cannot delete department with assigned employees")
    void cannotDeleteDepartmentWithAssignedEmployees() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByDepartmentId(departmentId)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.delete(departmentId, actorId))
            .isInstanceOf(DepartmentDeletionNotAllowedException.class)
            .hasMessage("Department cannot be deleted because employees are assigned to it");

        verify(projectDepartmentRepository, never()).existsByDepartmentIdAndProjectStatus(any(), any());
        verify(departmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cannot delete department linked to active projects")
    void cannotDeleteDepartmentLinkedToActiveProjects() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByDepartmentId(departmentId)).thenReturn(false);
        when(projectDepartmentRepository.existsByDepartmentIdAndProjectStatus(eq(departmentId), any()))
            .thenReturn(true);

        assertThatThrownBy(() -> departmentService.delete(departmentId, actorId))
            .isInstanceOf(DepartmentDeletionNotAllowedException.class)
            .hasMessage("Department cannot be deleted because it is linked to active projects");

        verify(departmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletes department when no blocking constraints exist")
    void deletesDepartmentWhenNoBlockingConstraintsExist() {
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByDepartmentId(departmentId)).thenReturn(false);
        when(projectDepartmentRepository.existsByDepartmentIdAndProjectStatus(eq(departmentId), any()))
            .thenReturn(false);

        departmentService.delete(departmentId, actorId);

        verify(departmentRepository).delete(department);
        verify(auditLogService).log(eq(actorId), any(), eq("department"), eq(departmentId), eq(department), eq(null));
    }

    @Test
    @DisplayName("deactivate path works when department exists")
    void deactivatePathWorksWhenDepartmentExists() {
        DepartmentDto response = new DepartmentDto(departmentId, "Engineering", "ENG", null, false, 0L, 0L, 0L);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(departmentMapper.toDto(department)).thenReturn(response);

        DepartmentDto result = departmentService.deactivate(departmentId, actorId);

        assertThat(result.isActive()).isFalse();
        assertThat(department.isActive()).isFalse();
        verify(departmentRepository).save(department);
    }

    @Test
    @DisplayName("department manager sees only scoped department with counts")
    void departmentManagerSeesOnlyScopedDepartmentWithCounts() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);

        List<UserRole> effectiveRoles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DEPT_MANAGER").isActive(true).build())
                .build()
        );
        Employee employee = Employee.builder()
            .id(employeeId)
            .userId(userId)
            .departmentId(departmentId)
            .build();

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(effectiveRoles);
        when(accessScopeService.hasAdministrationOrHrVisibility(effectiveRoles)).thenReturn(false);
        when(accessScopeService.hasAnyRole(effectiveRoles, "DEPT_MANAGER")).thenReturn(true);
        when(accessScopeService.findEmployee(userId)).thenReturn(Optional.of(employee));
        when(accessScopeService.resolveDepartmentManagerDepartmentId(effectiveRoles, employee))
            .thenReturn(Optional.of(departmentId));
        when(departmentRepository.findAllByIdIn(List.of(departmentId), pageable))
            .thenReturn(new PageImpl<>(List.of(department), pageable, 1));
        when(departmentMapper.toDto(department))
            .thenReturn(new DepartmentDto(departmentId, "Engineering", "ENG", null, true, 0L, 0L, 0L));
        when(employeeRepository.countByDepartmentId(departmentId)).thenReturn(7L);
        when(projectDepartmentRepository.countByDepartmentId(departmentId)).thenReturn(3L);
        when(projectAssignmentRepository.countActiveByDepartmentId(departmentId, LocalDate.now())).thenReturn(11L);

        Page<DepartmentDto> result = departmentService.getAll(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(departmentId);
        assertThat(result.getContent().get(0).employeeCount()).isEqualTo(7L);
        assertThat(result.getContent().get(0).projectCount()).isEqualTo(3L);
        assertThat(result.getContent().get(0).projectAssignmentCount()).isEqualTo(11L);
    }

    @Test
    @DisplayName("department manager cannot read another department")
    void departmentManagerCannotReadAnotherDepartment() {
        UUID userId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();

        List<UserRole> effectiveRoles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DEPT_MANAGER").isActive(true).build())
                .build()
        );
        Employee employee = Employee.builder()
            .userId(userId)
            .departmentId(departmentId)
            .build();

        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(effectiveRoles);
        when(accessScopeService.hasAdministrationOrHrVisibility(effectiveRoles)).thenReturn(false);
        when(accessScopeService.hasAnyRole(effectiveRoles, "DEPT_MANAGER")).thenReturn(true);
        when(accessScopeService.findEmployee(userId)).thenReturn(Optional.of(employee));
        when(accessScopeService.resolveDepartmentManagerDepartmentId(effectiveRoles, employee))
            .thenReturn(Optional.of(departmentId));

        assertThatThrownBy(() -> departmentService.getById(otherDepartmentId, userId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("You are not allowed to access this department");
    }
}
