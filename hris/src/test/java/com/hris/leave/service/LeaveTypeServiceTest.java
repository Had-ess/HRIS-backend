package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.dto.LeaveTypeCreateDto;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.entity.ValidatorSource;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveTypeService Unit Tests")
class LeaveTypeServiceTest {

    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private ValidationWorkflowRepository validationWorkflowRepository;

    @InjectMocks
    private LeaveTypeService leaveTypeService;

    private UUID workflowId;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
    }

    @Test
    @DisplayName("create assigns active leave validation workflow")
    void createAssignsActiveLeaveValidationWorkflow() {
        ValidationWorkflow workflow = ValidationWorkflow.builder()
            .id(workflowId)
            .code("LEAVE_STANDARD")
            .name("Leave Standard")
            .usage(ValidationUsage.LEAVE)
            .validatorSource(ValidatorSource.TEAM_HIERARCHY)
            .validationMode(ValidationMode.ONE_REQUIRED)
            .fallbackMode(ValidationFallbackMode.HR_QUEUE)
            .active(true)
            .build();
        when(validationWorkflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(invocation -> {
            LeaveType leaveType = invocation.getArgument(0);
            leaveType.setId(UUID.randomUUID());
            return leaveType;
        });

        var dto = leaveRequest("ANNUAL", workflowId);

        var result = leaveTypeService.create(dto);

        assertThat(result.validationWorkflowId()).isEqualTo(workflowId);
        assertThat(result.validationWorkflowCode()).isEqualTo("LEAVE_STANDARD");
        assertThat(result.validationWorkflowName()).isEqualTo("Leave Standard");
    }

    @Test
    @DisplayName("create rejects inactive validation workflow")
    void createRejectsInactiveValidationWorkflow() {
        ValidationWorkflow workflow = ValidationWorkflow.builder()
            .id(workflowId)
            .usage(ValidationUsage.LEAVE)
            .active(false)
            .build();
        when(validationWorkflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> leaveTypeService.create(leaveRequest("ANNUAL", workflowId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only active validation workflows can be assigned to leave types");
    }

    @Test
    @DisplayName("create rejects unknown validation workflow")
    void createRejectsUnknownValidationWorkflow() {
        when(validationWorkflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveTypeService.create(leaveRequest("ANNUAL", workflowId)))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Validation workflow not found");
    }

    private LeaveTypeCreateDto leaveRequest(String code, UUID assignedWorkflowId) {
        return new LeaveTypeCreateDto(code, "Annual Leave", true, false, true, assignedWorkflowId);
    }
}
