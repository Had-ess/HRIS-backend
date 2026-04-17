package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.DepartmentCreateDto;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.entity.Department;
import com.hris.auth.mapper.DepartmentMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<DepartmentDto> getAll(Pageable pageable) {
        return departmentRepository.findAll(pageable).map(departmentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public DepartmentDto getById(UUID id) {
        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        return departmentMapper.toDto(dept);
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
        return departmentMapper.toDto(saved);
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
        return departmentMapper.toDto(saved);
    }
}
