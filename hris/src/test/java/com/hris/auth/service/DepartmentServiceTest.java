package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.DepartmentCreateDto;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.dto.DepartmentUpdateDto;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.mapper.DepartmentMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DepartmentDeletionNotAllowedException;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ProjectDepartmentRepository projectDepartmentRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private DepartmentService service;

    private final UUID departmentId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder()
            .id(departmentId).name("Engineering").code("ENG").isActive(true).build();
        lenient().when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        lenient().when(departmentRepository.save(any(Department.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(departmentMapper.toDto(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getHeadEmployeeId(),
                d.getParentDepartmentId(), d.isActive(), 0L, 0L, 0L, d.getOpenings());
        });
    }

    private Employee employeeIn(UUID employeeId, UUID deptId, EmployeeStatus status) {
        return Employee.builder()
            .id(employeeId).userId(UUID.randomUUID()).employeeCode("EMP-1")
            .departmentId(deptId).status(status).build();
    }

    @Test
    @DisplayName("update applies head and parent with full-apply semantics — null clears")
    void updateClearsHeadAndParentWhenNull() {
        UUID previousHeadId = UUID.randomUUID();
        department.setHeadEmployeeId(previousHeadId);
        department.setParentDepartmentId(UUID.randomUUID());
        when(employeeRepository.findById(previousHeadId)).thenReturn(Optional.of(
            employeeIn(previousHeadId, departmentId, EmployeeStatus.ACTIVE)));

        DepartmentDto result = service.update(departmentId,
            new DepartmentUpdateDto(null, null, null, null, null), actorId);

        assertThat(result.headEmployeeId()).isNull();
        assertThat(result.parentDepartmentId()).isNull();
        verify(applicationEventPublisher).publishEvent((Object) any());
    }

    @Test
    @DisplayName("update rejects a head who is not a member of the department")
    void updateRejectsHeadOutsideDepartment() {
        UUID headId = UUID.randomUUID();
        when(employeeRepository.findById(headId)).thenReturn(Optional.of(
            employeeIn(headId, UUID.randomUUID(), EmployeeStatus.ACTIVE)));

        assertThatThrownBy(() -> service.update(departmentId,
            new DepartmentUpdateDto(null, null, headId, null, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("member of the department");
    }

    @Test
    @DisplayName("update rejects a non-active head")
    void updateRejectsInactiveHead() {
        UUID headId = UUID.randomUUID();
        when(employeeRepository.findById(headId)).thenReturn(Optional.of(
            employeeIn(headId, departmentId, EmployeeStatus.TERMINATED)));

        assertThatThrownBy(() -> service.update(departmentId,
            new DepartmentUpdateDto(null, null, headId, null, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
    }

    @Test
    @DisplayName("update rejects the department as its own parent")
    void updateRejectsSelfParent() {
        assertThatThrownBy(() -> service.update(departmentId,
            new DepartmentUpdateDto(null, null, null, departmentId, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("own parent");
    }

    @Test
    @DisplayName("update rejects a parent assignment that closes a hierarchy cycle")
    void updateRejectsParentCycle() {
        // child already has this department as its parent: dept ← child, then dept.parent = child
        UUID childId = UUID.randomUUID();
        Department child = Department.builder()
            .id(childId).name("Platform").code("PLT").isActive(true)
            .parentDepartmentId(departmentId).build();
        when(departmentRepository.findById(childId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.update(departmentId,
            new DepartmentUpdateDto(null, null, null, childId, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("update rejects an inactive parent")
    void updateRejectsInactiveParent() {
        UUID parentId = UUID.randomUUID();
        Department parent = Department.builder()
            .id(parentId).name("Ops").code("OPS").isActive(false).build();
        when(departmentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.update(departmentId,
            new DepartmentUpdateDto(null, null, null, parentId, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
    }

    @Test
    @DisplayName("create validates that the head exists and is active (membership skipped: new dept)")
    void createRejectsInactiveHead() {
        UUID headId = UUID.randomUUID();
        when(employeeRepository.findById(headId)).thenReturn(Optional.of(
            employeeIn(headId, UUID.randomUUID(), EmployeeStatus.INACTIVE)));

        assertThatThrownBy(() -> service.create(
            new DepartmentCreateDto("Data", "DATA", headId, null, true), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletion is blocked while child departments exist")
    void deleteBlockedByChildDepartments() {
        when(employeeRepository.existsByDepartmentId(departmentId)).thenReturn(false);
        when(departmentRepository.existsByParentDepartmentId(departmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(departmentId, actorId))
            .isInstanceOf(DepartmentDeletionNotAllowedException.class)
            .hasMessageContaining("child departments");
        verify(departmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletion is blocked while teams belong to the department")
    void deleteBlockedByTeams() {
        when(employeeRepository.existsByDepartmentId(departmentId)).thenReturn(false);
        when(departmentRepository.existsByParentDepartmentId(departmentId)).thenReturn(false);
        when(teamRepository.existsByDepartmentId(departmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(departmentId, actorId))
            .isInstanceOf(DepartmentDeletionNotAllowedException.class)
            .hasMessageContaining("teams");
        verify(departmentRepository, never()).delete(any());
    }
}
