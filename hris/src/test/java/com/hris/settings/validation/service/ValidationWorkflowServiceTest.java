package com.hris.settings.validation.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.PermissionRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.settings.validation.dto.ValidationWorkflowMutationDto;
import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidatorSource;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationWorkflowService Unit Tests")
class ValidationWorkflowServiceTest {

    @Mock private ValidationWorkflowRepository repository;
    @Mock private AccessProfileRepository accessProfileRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private AuditLogService auditLogService;

    private ValidationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ValidationWorkflowService(repository, accessProfileRepository, permissionRepository, leaveTypeRepository, auditLogService);
    }

    @Test
    @DisplayName("create requires min validators for MIN_N")
    void createRequiresMinValidatorsForMinN() {
        ValidationWorkflowMutationDto dto = new ValidationWorkflowMutationDto(
            "LEAVE_STANDARD",
            "Leave standard workflow",
            ValidationUsage.LEAVE,
            ValidatorSource.TEAM_HIERARCHY,
            ValidationMode.MIN_N,
            null,
            ValidationFallbackMode.HR_QUEUE,
            null,
            null,
            true,
            false
        );

        assertThatThrownBy(() -> service.create(dto, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("minValidators is required when validation mode is MIN_N");
    }

    @Test
    @DisplayName("create requires fallback profile when fallback mode is SPECIFIC_PROFILE")
    void createRequiresFallbackProfileForSpecificProfileMode() {
        ValidationWorkflowMutationDto dto = new ValidationWorkflowMutationDto(
            "LEAVE_STANDARD",
            "Leave standard workflow",
            ValidationUsage.LEAVE,
            ValidatorSource.TEAM_HIERARCHY,
            ValidationMode.ONE_REQUIRED,
            null,
            ValidationFallbackMode.SPECIFIC_PROFILE,
            null,
            null,
            true,
            false
        );

        assertThatThrownBy(() -> service.create(dto, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("fallbackProfileId is required when fallback mode is SPECIFIC_PROFILE");
    }

    @Test
    @DisplayName("create requires fallback permission when fallback mode is SPECIFIC_PERMISSION")
    void createRequiresFallbackPermissionForSpecificPermissionMode() {
        ValidationWorkflowMutationDto dto = new ValidationWorkflowMutationDto(
            "LEAVE_STANDARD",
            "Leave standard workflow",
            ValidationUsage.LEAVE,
            ValidatorSource.TEAM_HIERARCHY,
            ValidationMode.ONE_REQUIRED,
            null,
            ValidationFallbackMode.SPECIFIC_PERMISSION,
            null,
            null,
            true,
            false
        );

        assertThatThrownBy(() -> service.create(dto, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("fallbackPermissionCode is required when fallback mode is SPECIFIC_PERMISSION");
    }

    @Test
    @DisplayName("create validates fallback references")
    void createValidatesFallbackReferences() {
        UUID profileId = UUID.randomUUID();
        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.empty());

        ValidationWorkflowMutationDto dto = new ValidationWorkflowMutationDto(
            "LEAVE_STANDARD",
            "Leave standard workflow",
            ValidationUsage.LEAVE,
            ValidatorSource.TEAM_HIERARCHY,
            ValidationMode.ONE_REQUIRED,
            null,
            ValidationFallbackMode.SPECIFIC_PROFILE,
            profileId,
            null,
            true,
            false
        );

        assertThatThrownBy(() -> service.create(dto, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Fallback access profile does not exist");
    }
}
