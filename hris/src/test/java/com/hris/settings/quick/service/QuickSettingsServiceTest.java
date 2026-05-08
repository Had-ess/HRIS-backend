package com.hris.settings.quick.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.settings.calendar.entity.HrCalendar;
import com.hris.settings.calendar.repository.HrCalendarRepository;
import com.hris.settings.quick.dto.QuickSettingsMutationDto;
import com.hris.settings.quick.entity.EnterpriseSettings;
import com.hris.settings.quick.repository.EnterpriseSettingsRepository;
import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.entity.ValidatorSource;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuickSettingsService Unit Tests")
class QuickSettingsServiceTest {

    @Mock private EnterpriseSettingsRepository repository;
    @Mock private HrCalendarRepository hrCalendarRepository;
    @Mock private ValidationWorkflowRepository validationWorkflowRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private QuickSettingsService service;

    @Test
    @DisplayName("update persists active calendar and leave workflow defaults")
    void updatePersistsDefaults() {
        UUID workflowId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        EnterpriseSettings settings = EnterpriseSettings.builder().id(UUID.randomUUID()).singletonKey(true).build();
        when(repository.findFirstBySingletonKeyTrue()).thenReturn(Optional.of(settings));
        when(validationWorkflowRepository.findById(workflowId)).thenReturn(Optional.of(ValidationWorkflow.builder()
            .id(workflowId)
            .code("LEAVE_DEFAULT")
            .name("Leave default")
            .usage(ValidationUsage.LEAVE)
            .validatorSource(ValidatorSource.TEAM_HIERARCHY)
            .validationMode(ValidationMode.ONE_REQUIRED)
            .fallbackMode(ValidationFallbackMode.HR_QUEUE)
            .active(true)
            .build()));
        when(hrCalendarRepository.findById(calendarId)).thenReturn(Optional.of(HrCalendar.builder()
            .id(calendarId)
            .code("TN")
            .name("Tunisia")
            .timezone("Africa/Tunis")
            .hoursPerDay(8)
            .source("MANUAL")
            .active(true)
            .build()));
        when(repository.save(any(EnterpriseSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(new QuickSettingsMutationDto(
            2, 4, 16, "MON_FRI", workflowId, 24, 48, calendarId, 8
        ), actorId);

        assertThat(result.defaultValidationWorkflowId()).isEqualTo(workflowId);
        assertThat(result.activeCalendarId()).isEqualTo(calendarId);
        assertThat(result.workingHoursPerDay()).isEqualTo(8);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("update rejects inactive calendar")
    void updateRejectsInactiveCalendar() {
        UUID calendarId = UUID.randomUUID();
        when(repository.findFirstBySingletonKeyTrue()).thenReturn(Optional.of(EnterpriseSettings.builder().singletonKey(true).build()));
        when(hrCalendarRepository.findById(calendarId)).thenReturn(Optional.of(HrCalendar.builder()
            .id(calendarId)
            .active(false)
            .build()));

        assertThatThrownBy(() -> service.update(new QuickSettingsMutationDto(
            null, null, null, null, null, null, null, calendarId, null
        ), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Active calendar must reference an active HR calendar");
    }

    @Test
    @DisplayName("update rejects unknown workflow")
    void updateRejectsUnknownWorkflow() {
        UUID workflowId = UUID.randomUUID();
        when(repository.findFirstBySingletonKeyTrue()).thenReturn(Optional.of(EnterpriseSettings.builder().singletonKey(true).build()));
        when(validationWorkflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new QuickSettingsMutationDto(
            null, null, null, null, workflowId, null, null, null, null
        ), UUID.randomUUID()))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Validation workflow not found");
    }
}
