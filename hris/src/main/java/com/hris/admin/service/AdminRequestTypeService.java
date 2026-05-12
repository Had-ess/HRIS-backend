package com.hris.admin.service;

import com.hris.admin.dto.AdminRequestTypeCreateDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.dto.AdminRequestTypeUpdateDto;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestTypeService {

    private final AdminRequestTypeRepository adminRequestTypeRepository;
    private final AdminRequestRepository adminRequestRepository;

    @Transactional(readOnly = true)
    public List<AdminRequestTypeDto> getAll(boolean activeOnly) {
        return (activeOnly
                ? adminRequestTypeRepository.findByIsActiveTrueOrderByNameAsc()
                : adminRequestTypeRepository.findAllByOrderByNameAsc())
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminRequestTypeDto getById(UUID id) {
        return toDto(findType(id));
    }

    @Transactional
    public AdminRequestTypeDto create(AdminRequestTypeCreateDto dto) {
        String normalizedCode = normalizeRequired(dto.code(), "code");
        if (adminRequestTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Admin request type code already exists");
        }

        Instant now = Instant.now();
        AdminRequestType saved = adminRequestTypeRepository.save(AdminRequestType.builder()
            .code(normalizedCode)
            .name(normalizeRequired(dto.name(), "name"))
            .description(normalizeOptional(dto.description()))
            .requiresAttachment(Boolean.TRUE.equals(dto.requiresAttachment()))
            .slaHours(dto.slaHours())
            .isActive(dto.isActive() == null || dto.isActive())
            .createdAt(now)
            .updatedAt(now)
            .build());
        return toDto(saved);
    }

    @Transactional
    public AdminRequestTypeDto update(UUID id, AdminRequestTypeUpdateDto dto) {
        AdminRequestType existing = findType(id);

        if (dto.code() != null) {
            String normalizedCode = normalizeRequired(dto.code(), "code");
            if (adminRequestTypeRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
                throw new IllegalArgumentException("Admin request type code already exists");
            }
            existing.setCode(normalizedCode);
        }
        if (dto.name() != null) {
            existing.setName(normalizeRequired(dto.name(), "name"));
        }
        if (dto.description() != null) {
            existing.setDescription(normalizeOptional(dto.description()));
        }
        if (dto.requiresAttachment() != null) {
            existing.setRequiresAttachment(dto.requiresAttachment());
        }
        if (dto.slaHours() != null) {
            existing.setSlaHours(dto.slaHours());
        }
        if (dto.isActive() != null) {
            existing.setActive(dto.isActive());
        }
        existing.setUpdatedAt(Instant.now());
        return toDto(adminRequestTypeRepository.save(existing));
    }

    @Transactional
    public void deactivate(UUID id) {
        AdminRequestType existing = findType(id);
        existing.setActive(false);
        existing.setUpdatedAt(Instant.now());
        adminRequestTypeRepository.save(existing);
    }

    @Transactional
    public void deleteOrDeactivate(UUID id) {
        AdminRequestType existing = findType(id);
        if (adminRequestRepository.existsByTypeId(id)) {
            existing.setActive(false);
            existing.setUpdatedAt(Instant.now());
            adminRequestTypeRepository.save(existing);
            return;
        }
        adminRequestTypeRepository.delete(existing);
    }

    private AdminRequestType findType(UUID id) {
        return adminRequestTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Admin request type not found"));
    }

    private AdminRequestTypeDto toDto(AdminRequestType type) {
        return new AdminRequestTypeDto(
            type.getId(),
            type.getCode(),
            type.getName(),
            type.getDescription(),
            type.isRequiresAttachment(),
            type.getSlaHours(),
            type.isActive(),
            type.getCreatedAt(),
            type.getUpdatedAt()
        );
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
