package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.DepartmentCreateDto;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import com.hris.auth.mapper.DepartmentMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DepartmentDeletionNotAllowedException;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AccessScopeService accessScopeService;
    private final DepartmentMapper departmentMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<DepartmentDto> getAll(UUID userId, Pageable pageable) {
        DepartmentReadScope scope = resolveReadScope(userId);

        return switch (scope.type()) {
            case ALL -> departmentRepository.findAll(pageable).map(this::toDto);
            case DEPARTMENT -> {
                if (scope.departmentId() == null) {
                    yield Page.empty(pageable);
                }
                yield departmentRepository.findAllByIdIn(List.of(scope.departmentId()), pageable).map(this::toDto);
            }
        };
    }

    @Transactional(readOnly = true)
    public DepartmentDto getById(UUID id, UUID userId) {
        DepartmentReadScope scope = resolveReadScope(userId);
        if (scope.type() == DepartmentReadScopeType.DEPARTMENT && !id.equals(scope.departmentId())) {
            throw new AccessDeniedException("You are not allowed to access this department");
        }

        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        return toDto(dept);
    }

    @Transactional
    public DepartmentDto create(DepartmentCreateDto dto, UUID actorId) {
        Department dept = Department.builder()
            .name(dto.name())
            .code(dto.code())
            .headEmployeeId(dto.headEmployeeId())
            .isActive(dto.isActive() != null ? dto.isActive() : true)
            .build();

        Department saved = departmentRepository.save(dept);
        auditLogService.log(actorId, AuditAction.CREATE, "department",
            saved.getId(), null, saved);
        return toDto(saved);
    }

    @Transactional
    public DepartmentDto update(UUID id, DepartmentCreateDto dto, UUID actorId) {
        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));

        Department previous = Department.builder()
            .name(dept.getName()).code(dept.getCode())
            .headEmployeeId(dept.getHeadEmployeeId()).isActive(dept.isActive())
            .build();

        if (dto.name() != null) dept.setName(dto.name());
        if (dto.code() != null) dept.setCode(dto.code());
        if (dto.headEmployeeId() != null) dept.setHeadEmployeeId(dto.headEmployeeId());
        if (dto.isActive() != null) dept.setActive(dto.isActive());

        Department saved = departmentRepository.save(dept);
        auditLogService.log(actorId, AuditAction.UPDATE, "department",
            saved.getId(), previous, saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        Department department = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));

        validateDeletion(department.getId());

        departmentRepository.delete(department);
        auditLogService.log(actorId, AuditAction.DELETE, "department",
            department.getId(), department, null);
    }

    @Transactional
    public DepartmentDto deactivate(UUID id, UUID actorId) {
        Department department = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));

        Department previous = Department.builder()
            .id(department.getId())
            .name(department.getName())
            .code(department.getCode())
            .headEmployeeId(department.getHeadEmployeeId())
            .isActive(department.isActive())
            .build();

        department.setActive(false);
        Department saved = departmentRepository.save(department);
        auditLogService.log(actorId, AuditAction.UPDATE, "department",
            saved.getId(), previous, saved);
        return toDto(saved);
    }

    private void validateDeletion(UUID departmentId) {
        if (employeeRepository.existsByDepartmentId(departmentId)) {
            throw new DepartmentDeletionNotAllowedException(
                "Department cannot be deleted because employees are assigned to it");
        }

        if (projectDepartmentRepository.existsByDepartmentIdAndProjectStatus(
                departmentId, ProjectStatus.ACTIVE)) {
            throw new DepartmentDeletionNotAllowedException(
                "Department cannot be deleted because it is linked to active projects");
        }
    }

    private DepartmentDto toDto(Department department) {
        DepartmentDto base = departmentMapper.toDto(department);
        if (base == null) {
            return null;
        }

        return new DepartmentDto(
            base.id(),
            base.name(),
            base.code(),
            base.headEmployeeId(),
            base.isActive(),
            employeeRepository.countByDepartmentId(base.id()),
            projectDepartmentRepository.countByDepartmentId(base.id()),
            projectAssignmentRepository.countActiveByDepartmentId(base.id(), LocalDate.now())
        );
    }

    private DepartmentReadScope resolveReadScope(UUID userId) {
        var effectiveRoles = accessScopeService.getEffectiveRoles(userId);

        if (accessScopeService.hasAdministrationOrHrVisibility(effectiveRoles)) {
            return DepartmentReadScope.all();
        }

        if (!accessScopeService.hasAnyRole(effectiveRoles, "DEPT_MANAGER")) {
            return DepartmentReadScope.all();
        }

        return DepartmentReadScope.department(
            accessScopeService.resolveDepartmentManagerDepartmentId(
                effectiveRoles,
                accessScopeService.findEmployee(userId).orElse(null)
            ).orElse(null)
        );
    }

    private record DepartmentReadScope(DepartmentReadScopeType type, UUID departmentId) {
        static DepartmentReadScope all() {
            return new DepartmentReadScope(DepartmentReadScopeType.ALL, null);
        }

        static DepartmentReadScope department(UUID departmentId) {
            return new DepartmentReadScope(DepartmentReadScopeType.DEPARTMENT, departmentId);
        }
    }

    private enum DepartmentReadScopeType {
        ALL,
        DEPARTMENT
    }
}
