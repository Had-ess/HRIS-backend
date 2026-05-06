package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.dto.LeaveTypeCreateDto;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.dto.LeaveTypeUpdateDto;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    @Transactional(readOnly = true)
    public List<LeaveTypeDto> getAllActive() {
        return leaveTypeRepository.findByIsActiveTrue().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public LeaveTypeDto getDtoById(UUID id) {
        return leaveTypeRepository.findById(id)
            .map(this::toDto)
            .orElse(null);
    }

    @Transactional
    public LeaveTypeDto create(LeaveTypeCreateDto dto) {
        LeaveType saved = leaveTypeRepository.save(LeaveType.builder()
            .code(dto.code().trim())
            .name(dto.name().trim())
            .isPaid(dto.isPaid() == null || dto.isPaid())
            .requiresJustification(Boolean.TRUE.equals(dto.requiresJustification()))
            .isActive(dto.isActive() == null || dto.isActive())
            .build());
        return toDto(saved);
    }

    @Transactional
    public LeaveTypeDto update(UUID id, LeaveTypeUpdateDto dto) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        existing.setCode(dto.code().trim());
        existing.setName(dto.name().trim());
        existing.setPaid(dto.isPaid());
        existing.setRequiresJustification(dto.requiresJustification());
        existing.setActive(dto.isActive());
        return toDto(leaveTypeRepository.save(existing));
    }

    @Transactional
    public void deactivate(UUID id) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        existing.setActive(false);
        leaveTypeRepository.save(existing);
    }

    private LeaveTypeDto toDto(LeaveType leaveType) {
        return new LeaveTypeDto(
            leaveType.getId(),
            leaveType.getCode(),
            leaveType.getName(),
            leaveType.isPaid(),
            leaveType.isRequiresJustification(),
            leaveType.isActive()
        );
    }
}
