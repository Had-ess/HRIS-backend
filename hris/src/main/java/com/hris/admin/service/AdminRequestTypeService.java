package com.hris.admin.service;

import com.hris.admin.dto.AdminRequestTypeCreateDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.dto.AdminRequestTypeUpdateDto;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.mapper.AdminRequestMapper;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestTypeService {

    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final AdminRequestMapper adminRequestMapper;

    @Transactional(readOnly = true)
    public List<AdminRequestTypeDto> getAll() {
        return adminRequestTypeRepository.findAll().stream()
            .map(adminRequestMapper::toTypeDto)
            .toList();
    }

    @Transactional
    public AdminRequestTypeDto create(AdminRequestTypeCreateDto dto) {
        AdminRequestType saved = adminRequestTypeRepository.save(AdminRequestType.builder()
            .code(dto.code().trim())
            .name(dto.name().trim())
            .isActive(Boolean.TRUE.equals(dto.isActive()))
            .build());
        return adminRequestMapper.toTypeDto(saved);
    }

    @Transactional
    public AdminRequestTypeDto update(UUID id, AdminRequestTypeUpdateDto dto) {
        AdminRequestType existing = adminRequestTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Type not found"));
        existing.setCode(dto.code().trim());
        existing.setName(dto.name().trim());
        existing.setActive(dto.isActive());
        return adminRequestMapper.toTypeDto(adminRequestTypeRepository.save(existing));
    }

    @Transactional
    public void deactivate(UUID id) {
        AdminRequestType existing = adminRequestTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Type not found"));
        existing.setActive(false);
        adminRequestTypeRepository.save(existing);
    }
}
