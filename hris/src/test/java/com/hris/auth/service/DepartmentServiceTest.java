package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import com.hris.auth.mapper.DepartmentMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DepartmentDeletionNotAllowedException;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        DepartmentDto response = new DepartmentDto(departmentId, "Engineering", "ENG", null, false);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(departmentMapper.toDto(department)).thenReturn(response);

        DepartmentDto result = departmentService.deactivate(departmentId, actorId);

        assertThat(result.isActive()).isFalse();
        assertThat(department.isActive()).isFalse();
        verify(departmentRepository).save(department);
    }
}
