package com.hris.settings.validation.service;

import com.hris.access.dto.AccessProfileResponseDto;
import com.hris.access.entity.AccessProfile;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.entity.Permission;
import com.hris.auth.repository.PermissionRepository;
import com.hris.common.PageResponse;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.settings.validation.dto.ValidationWorkflowDto;
import com.hris.settings.validation.dto.ValidationWorkflowMutationDto;
import com.hris.settings.validation.dto.ValidationWorkflowOptionsDto;
import com.hris.settings.validation.entity.*;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ValidationWorkflowService {

    private final ValidationWorkflowRepository repository;
    private final AccessProfileRepository accessProfileRepository;
    private final PermissionRepository permissionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<ValidationWorkflowDto> getAll(Pageable pageable) {
        return PageResponse.of(repository.findAllByOrderByCodeAsc(pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ValidationWorkflowDto getById(UUID id) {
        return toDto(getEntity(id));
    }

    @Transactional
    public ValidationWorkflowDto create(ValidationWorkflowMutationDto dto, UUID actorId) {
        validate(dto, null);
        ValidationWorkflow workflow = buildEntity(dto);
        ValidationWorkflow saved = repository.save(workflow);
        auditLogService.log(actorId, AuditAction.CREATE, "validation_workflow", saved.getId(), null, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public ValidationWorkflowDto update(UUID id, ValidationWorkflowMutationDto dto, UUID actorId) {
        ValidationWorkflow existing = getEntity(id);
        validate(dto, id);
        Map<String, Object> previous = snapshot(existing);

        existing.setCode(normalizeCode(dto.code()));
        existing.setName(dto.name().trim());
        existing.setUsage(dto.usage());
        existing.setValidatorSource(dto.validatorSource());
        existing.setValidationMode(dto.validationMode());
        existing.setMinValidators(dto.validationMode() == ValidationMode.MIN_N ? dto.minValidators() : null);
        existing.setFallbackMode(dto.fallbackMode());
        existing.setFallbackProfileId(dto.fallbackMode() == ValidationFallbackMode.SPECIFIC_PROFILE ? dto.fallbackProfileId() : null);
        existing.setFallbackPermissionCode(dto.fallbackMode() == ValidationFallbackMode.SPECIFIC_PERMISSION
            ? normalizePermissionCode(dto.fallbackPermissionCode())
            : null);
        existing.setActive(dto.active() == null || dto.active());
        existing.setDefaultWorkflow(Boolean.TRUE.equals(dto.defaultWorkflow()));

        ValidationWorkflow saved = repository.save(existing);
        auditLogService.log(actorId, AuditAction.UPDATE, "validation_workflow", saved.getId(), previous, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        ValidationWorkflow workflow = getEntity(id);
        ensureNotReferencedByLeaveTypes(workflow.getId());
        Map<String, Object> previous = snapshot(workflow);
        workflow.setActive(false);
        repository.save(workflow);
        auditLogService.log(actorId, AuditAction.UPDATE, "validation_workflow", workflow.getId(), previous, snapshot(workflow));
    }

    @Transactional
    public void hardDelete(UUID id, UUID actorId) {
        ValidationWorkflow workflow = getEntity(id);
        if (workflow.isActive()) {
            throw new IllegalStateException("Validation workflow must be deactivated before deletion");
        }
        ensureNotReferencedByLeaveTypes(workflow.getId());
        try {
            repository.delete(workflow);
            repository.flush();
            auditLogService.log(actorId, AuditAction.DELETE, "validation_workflow", workflow.getId(), snapshot(workflow), null);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Validation workflow cannot be deleted because it is still referenced");
        }
    }

    @Transactional(readOnly = true)
    public ValidationWorkflowOptionsDto getOptions(ValidationUsage usage) {
        if (usage != ValidationUsage.LEAVE) {
            throw new IllegalArgumentException("Only LEAVE usage is supported");
        }

        List<AccessProfileResponseDto> profiles = accessProfileRepository.findAll().stream()
            .filter(AccessProfile::isActive)
            .map(profile -> new AccessProfileResponseDto(
                profile.getId(),
                profile.getCode(),
                profile.getDisplayKey(),
                profile.getDescriptionKey(),
                profile.isSystemProfile(),
                profile.isActive(),
                0
            ))
            .sorted(java.util.Comparator.comparing(AccessProfileResponseDto::displayKey))
            .toList();

        List<PermissionResponseDto> permissions = permissionRepository.findAllByOrderByResourceAscActionAsc().stream()
            .filter(Permission::isActive)
            .map(permission -> new PermissionResponseDto(
                permission.getId(),
                permission.getName(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription(),
                permission.isActive()
            ))
            .toList();

        return new ValidationWorkflowOptionsDto(
            List.of(ValidationUsage.LEAVE.name()),
            List.of(ValidatorSource.TEAM_HIERARCHY.name()),
            Arrays.stream(ValidationMode.values()).map(Enum::name).toList(),
            Arrays.stream(ValidationFallbackMode.values()).map(Enum::name).toList(),
            profiles,
            permissions
        );
    }

    private void validate(ValidationWorkflowMutationDto dto, UUID currentId) {
        boolean duplicateCode = currentId == null
            ? repository.existsByCodeIgnoreCase(dto.code().trim())
            : repository.existsByCodeIgnoreCaseAndIdNot(dto.code().trim(), currentId);
        if (duplicateCode) {
            throw new IllegalStateException("Validation workflow code must be unique");
        }
        if (dto.usage() != ValidationUsage.LEAVE) {
            throw new IllegalArgumentException("Only LEAVE usage is supported");
        }
        if (dto.validatorSource() != ValidatorSource.TEAM_HIERARCHY) {
            throw new IllegalArgumentException("Only TEAM_HIERARCHY validator source is supported");
        }
        if (Boolean.TRUE.equals(dto.defaultWorkflow())) {
            boolean defaultExists = currentId == null
                ? repository.findFirstByUsageAndActiveTrueAndDefaultWorkflowTrue(dto.usage()).isPresent()
                : repository.existsByUsageAndDefaultWorkflowTrueAndIdNot(dto.usage(), currentId);
            if (defaultExists) {
                throw new IllegalStateException("Only one default validation workflow is allowed per usage");
            }
        }
        if (dto.validationMode() == ValidationMode.MIN_N) {
            if (dto.minValidators() == null) {
                throw new IllegalArgumentException("minValidators is required when validation mode is MIN_N");
            }
            if (dto.minValidators() < 1) {
                throw new IllegalArgumentException("minValidators must be greater than zero");
            }
        } else if (dto.minValidators() != null) {
            throw new IllegalArgumentException("minValidators is only allowed when validation mode is MIN_N");
        }

        switch (dto.fallbackMode()) {
            case SPECIFIC_PROFILE -> {
                if (dto.fallbackProfileId() == null) {
                    throw new IllegalArgumentException("fallbackProfileId is required when fallback mode is SPECIFIC_PROFILE");
                }
                if (accessProfileRepository.findById(dto.fallbackProfileId()).isEmpty()) {
                    throw new IllegalStateException("Fallback access profile does not exist");
                }
                if (dto.fallbackPermissionCode() != null && !dto.fallbackPermissionCode().isBlank()) {
                    throw new IllegalArgumentException("fallbackPermissionCode is not allowed when fallback mode is SPECIFIC_PROFILE");
                }
            }
            case SPECIFIC_PERMISSION -> {
                if (dto.fallbackPermissionCode() == null || dto.fallbackPermissionCode().isBlank()) {
                    throw new IllegalArgumentException("fallbackPermissionCode is required when fallback mode is SPECIFIC_PERMISSION");
                }
                if (permissionRepository.findByNameIgnoreCase(dto.fallbackPermissionCode().trim()).isEmpty()) {
                    throw new IllegalStateException("Fallback permission does not exist");
                }
                if (dto.fallbackProfileId() != null) {
                    throw new IllegalArgumentException("fallbackProfileId is not allowed when fallback mode is SPECIFIC_PERMISSION");
                }
            }
            case HR_QUEUE, BLOCK_SUBMISSION -> {
                if (dto.fallbackProfileId() != null) {
                    throw new IllegalArgumentException("fallbackProfileId is not allowed for this fallback mode");
                }
                if (dto.fallbackPermissionCode() != null && !dto.fallbackPermissionCode().isBlank()) {
                    throw new IllegalArgumentException("fallbackPermissionCode is not allowed for this fallback mode");
                }
            }
        }
    }

    private ValidationWorkflow getEntity(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Validation workflow not found"));
    }

    private ValidationWorkflow buildEntity(ValidationWorkflowMutationDto dto) {
        return ValidationWorkflow.builder()
            .code(normalizeCode(dto.code()))
            .name(dto.name().trim())
            .usage(dto.usage())
            .validatorSource(dto.validatorSource())
            .validationMode(dto.validationMode())
            .minValidators(dto.validationMode() == ValidationMode.MIN_N ? dto.minValidators() : null)
            .fallbackMode(dto.fallbackMode())
            .fallbackProfileId(dto.fallbackMode() == ValidationFallbackMode.SPECIFIC_PROFILE ? dto.fallbackProfileId() : null)
            .fallbackPermissionCode(dto.fallbackMode() == ValidationFallbackMode.SPECIFIC_PERMISSION
                ? normalizePermissionCode(dto.fallbackPermissionCode())
                : null)
            .active(dto.active() == null || dto.active())
            .defaultWorkflow(Boolean.TRUE.equals(dto.defaultWorkflow()))
            .build();
    }

    private ValidationWorkflowDto toDto(ValidationWorkflow workflow) {
        return new ValidationWorkflowDto(
            workflow.getId(),
            workflow.getCode(),
            workflow.getName(),
            workflow.getUsage().name(),
            workflow.getValidatorSource().name(),
            workflow.getValidationMode().name(),
            workflow.getMinValidators(),
            workflow.getFallbackMode().name(),
            workflow.getFallbackProfileId(),
            workflow.getFallbackPermissionCode(),
            workflow.isActive(),
            workflow.isDefaultWorkflow(),
            workflow.getCreatedAt(),
            workflow.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePermissionCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> snapshot(ValidationWorkflow workflow) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", workflow.getCode());
        state.put("name", workflow.getName());
        state.put("usage", workflow.getUsage());
        state.put("validatorSource", workflow.getValidatorSource());
        state.put("validationMode", workflow.getValidationMode());
        state.put("minValidators", workflow.getMinValidators());
        state.put("fallbackMode", workflow.getFallbackMode());
        state.put("fallbackProfileId", workflow.getFallbackProfileId());
        state.put("fallbackPermissionCode", workflow.getFallbackPermissionCode());
        state.put("active", workflow.isActive());
        state.put("defaultWorkflow", workflow.isDefaultWorkflow());
        return state;
    }

    private void ensureNotReferencedByLeaveTypes(UUID workflowId) {
        if (leaveTypeRepository.existsByValidationWorkflowId(workflowId)) {
            throw new IllegalStateException("Validation workflow is assigned to one or more leave types");
        }
    }
}
