package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.dto.LeaveTypeCreateDto;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.dto.LeaveTypeUpdateDto;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final ValidationWorkflowRepository validationWorkflowRepository;

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
        ValidationWorkflow workflow = resolveAssignableWorkflow(dto.validationWorkflowId());
        LeaveType saved = leaveTypeRepository.save(LeaveType.builder()
            .code(dto.code().trim().toUpperCase(Locale.ROOT))
            .name(dto.name().trim())
            .isPaid(dto.isPaid() == null || dto.isPaid())
            .requiresJustification(Boolean.TRUE.equals(dto.requiresJustification()))
            .isActive(dto.isActive() == null || dto.isActive())
            .validationWorkflowId(workflow != null ? workflow.getId() : null)
            .build());
        return toDto(saved, workflow);
    }

    @Transactional
    public LeaveTypeDto update(UUID id, LeaveTypeUpdateDto dto) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        ValidationWorkflow workflow = resolveAssignableWorkflow(dto.validationWorkflowId());
        existing.setCode(dto.code().trim().toUpperCase(Locale.ROOT));
        existing.setName(dto.name().trim());
        existing.setPaid(dto.isPaid());
        existing.setRequiresJustification(dto.requiresJustification());
        existing.setActive(dto.isActive());
        existing.setValidationWorkflowId(workflow != null ? workflow.getId() : null);
        return toDto(leaveTypeRepository.save(existing), workflow);
    }

    @Transactional
    public void deactivate(UUID id) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        existing.setActive(false);
        leaveTypeRepository.save(existing);
    }

    private LeaveTypeDto toDto(LeaveType leaveType) {
        ValidationWorkflow workflow = leaveType.getValidationWorkflowId() == null
            ? null
            : validationWorkflowRepository.findById(leaveType.getValidationWorkflowId()).orElse(null);
        return toDto(leaveType, workflow);
    }

    private LeaveTypeDto toDto(LeaveType leaveType, ValidationWorkflow workflow) {
        return new LeaveTypeDto(
            leaveType.getId(),
            leaveType.getCode(),
            leaveType.getName(),
            leaveType.isPaid(),
            leaveType.isRequiresJustification(),
            leaveType.isActive(),
            leaveType.getValidationWorkflowId(),
            workflow != null ? workflow.getCode() : null,
            workflow != null ? workflow.getName() : null
        );
    }

    private ValidationWorkflow resolveAssignableWorkflow(UUID workflowId) {
        if (workflowId == null) {
            return null;
        }
        ValidationWorkflow workflow = validationWorkflowRepository.findById(workflowId)
            .orElseThrow(() -> new EntityNotFoundException("Validation workflow not found"));
        if (!workflow.isActive()) {
            throw new IllegalArgumentException("Only active validation workflows can be assigned to leave types");
        }
        if (workflow.getUsage() != ValidationUsage.LEAVE) {
            throw new IllegalArgumentException("Only LEAVE validation workflows can be assigned to leave types");
        }
        return workflow;
    }
}
