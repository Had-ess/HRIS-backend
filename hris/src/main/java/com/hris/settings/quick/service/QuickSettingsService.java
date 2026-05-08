package com.hris.settings.quick.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.settings.calendar.entity.HrCalendar;
import com.hris.settings.calendar.repository.HrCalendarRepository;
import com.hris.settings.quick.dto.QuickSettingsDto;
import com.hris.settings.quick.dto.QuickSettingsMutationDto;
import com.hris.settings.quick.entity.EnterpriseSettings;
import com.hris.settings.quick.repository.EnterpriseSettingsRepository;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuickSettingsService {

    private final EnterpriseSettingsRepository repository;
    private final HrCalendarRepository hrCalendarRepository;
    private final ValidationWorkflowRepository validationWorkflowRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public QuickSettingsDto get() {
        return toDto(getOrCreateSingleton());
    }

    @Transactional
    public QuickSettingsDto update(QuickSettingsMutationDto dto, UUID actorId) {
        EnterpriseSettings settings = getOrCreateSingleton();
        EnterpriseSettings previous = copy(settings);

        ValidationWorkflow workflow = resolveWorkflow(dto.defaultValidationWorkflowId());
        HrCalendar calendar = resolveCalendar(dto.activeCalendarId());

        validatePositive("Monthly acquisition rate", dto.monthlyAcquisitionRate(), true);
        validatePositive("Max authorizations per month", dto.maxAuthorizationsPerMonth(), true);
        validatePositive("Max authorization hours", dto.maxAuthorizationHours(), true);
        validatePositive("Default workflow SLA hours", dto.defaultWorkflowSlaHours(), false);
        validatePositive("Default validation SLA hours", dto.defaultValidationSlaHours(), false);
        validateWorkingHours(dto.workingHoursPerDay());

        settings.setMonthlyAcquisitionRate(dto.monthlyAcquisitionRate());
        settings.setMaxAuthorizationsPerMonth(dto.maxAuthorizationsPerMonth());
        settings.setMaxAuthorizationHours(dto.maxAuthorizationHours());
        settings.setWorkWeekPattern(blankToNull(dto.workWeekPattern()));
        settings.setDefaultValidationWorkflowId(workflow != null ? workflow.getId() : null);
        settings.setDefaultWorkflowSlaHours(dto.defaultWorkflowSlaHours());
        settings.setDefaultValidationSlaHours(dto.defaultValidationSlaHours());
        settings.setActiveCalendarId(calendar != null ? calendar.getId() : null);
        settings.setWorkingHoursPerDay(dto.workingHoursPerDay());

        EnterpriseSettings saved = repository.save(settings);
        auditLogService.log(actorId, AuditAction.UPDATE, "quick_settings", saved.getId(), previous, saved);
        return toDto(saved);
    }

    @Transactional
    public EnterpriseSettings getOrCreateSingleton() {
        return repository.findFirstBySingletonKeyTrue().orElseGet(() ->
            repository.save(EnterpriseSettings.builder().singletonKey(true).build())
        );
    }

    private QuickSettingsDto toDto(EnterpriseSettings settings) {
        ValidationWorkflow workflow = settings.getDefaultValidationWorkflowId() == null
            ? null
            : validationWorkflowRepository.findById(settings.getDefaultValidationWorkflowId()).orElse(null);
        HrCalendar calendar = settings.getActiveCalendarId() == null
            ? null
            : hrCalendarRepository.findById(settings.getActiveCalendarId()).orElse(null);
        return new QuickSettingsDto(
            settings.getMonthlyAcquisitionRate(),
            settings.getMaxAuthorizationsPerMonth(),
            settings.getMaxAuthorizationHours(),
            settings.getWorkWeekPattern(),
            settings.getDefaultValidationWorkflowId(),
            workflow != null ? workflow.getCode() : null,
            workflow != null ? workflow.getName() : null,
            settings.getDefaultWorkflowSlaHours(),
            settings.getDefaultValidationSlaHours(),
            settings.getActiveCalendarId(),
            calendar != null ? calendar.getCode() : null,
            calendar != null ? calendar.getName() : null,
            settings.getWorkingHoursPerDay(),
            settings.getUpdatedAt()
        );
    }

    private ValidationWorkflow resolveWorkflow(UUID workflowId) {
        if (workflowId == null) {
            return null;
        }
        ValidationWorkflow workflow = validationWorkflowRepository.findById(workflowId)
            .orElseThrow(() -> new EntityNotFoundException("Validation workflow not found"));
        if (!workflow.isActive() || workflow.getUsage() != ValidationUsage.LEAVE) {
            throw new IllegalArgumentException("Default validation workflow must be an active LEAVE workflow");
        }
        return workflow;
    }

    private HrCalendar resolveCalendar(UUID calendarId) {
        if (calendarId == null) {
            return null;
        }
        HrCalendar calendar = hrCalendarRepository.findById(calendarId)
            .orElseThrow(() -> new EntityNotFoundException("HR calendar not found"));
        if (!calendar.isActive()) {
            throw new IllegalArgumentException("Active calendar must reference an active HR calendar");
        }
        return calendar;
    }

    private void validatePositive(String field, Integer value, boolean allowZero) {
        if (value == null) {
            return;
        }
        if (allowZero ? value < 0 : value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void validateWorkingHours(Integer value) {
        if (value == null) {
            return;
        }
        if (value < 1 || value > 24) {
            throw new IllegalArgumentException("Working hours per day must be between 1 and 24");
        }
    }

    private EnterpriseSettings copy(EnterpriseSettings settings) {
        return EnterpriseSettings.builder()
            .id(settings.getId())
            .singletonKey(settings.isSingletonKey())
            .monthlyAcquisitionRate(settings.getMonthlyAcquisitionRate())
            .maxAuthorizationsPerMonth(settings.getMaxAuthorizationsPerMonth())
            .maxAuthorizationHours(settings.getMaxAuthorizationHours())
            .workWeekPattern(settings.getWorkWeekPattern())
            .defaultValidationWorkflowId(settings.getDefaultValidationWorkflowId())
            .defaultWorkflowSlaHours(settings.getDefaultWorkflowSlaHours())
            .defaultValidationSlaHours(settings.getDefaultValidationSlaHours())
            .activeCalendarId(settings.getActiveCalendarId())
            .workingHoursPerDay(settings.getWorkingHoursPerDay())
            .updatedAt(settings.getUpdatedAt())
            .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
