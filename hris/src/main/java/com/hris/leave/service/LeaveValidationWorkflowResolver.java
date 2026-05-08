package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.leave.entity.LeaveType;
import com.hris.settings.quick.repository.EnterpriseSettingsRepository;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.repository.ValidationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveValidationWorkflowResolver {

    private final ValidationWorkflowRepository validationWorkflowRepository;
    private final EnterpriseSettingsRepository enterpriseSettingsRepository;

    @Transactional(readOnly = true)
    public ValidationWorkflow resolveForLeaveType(LeaveType leaveType) {
        if (leaveType.getValidationWorkflowId() != null) {
            ValidationWorkflow explicitWorkflow = validationWorkflowRepository.findById(leaveType.getValidationWorkflowId())
                .orElseThrow(() -> new EntityNotFoundException("Validation workflow not found"));
            validateUsable(explicitWorkflow);
            return explicitWorkflow;
        }

        ValidationWorkflow quickSettingWorkflow = enterpriseSettingsRepository.findFirstBySingletonKeyTrue()
            .filter(settings -> settings.getDefaultValidationWorkflowId() != null)
            .flatMap(settings -> validationWorkflowRepository.findById(settings.getDefaultValidationWorkflowId()))
            .orElse(null);
        if (quickSettingWorkflow != null) {
            validateUsable(quickSettingWorkflow);
            return quickSettingWorkflow;
        }

        return validationWorkflowRepository.findFirstByUsageAndActiveTrueAndDefaultWorkflowTrue(ValidationUsage.LEAVE)
            .map(workflow -> {
                validateUsable(workflow);
                return workflow;
            })
            .orElseThrow(() -> new InvalidWorkflowStateException(
                "No validation workflow is configured for leave type '" + leaveType.getName() + "'"));
    }

    private void validateUsable(ValidationWorkflow workflow) {
        if (!workflow.isActive()) {
            throw new InvalidWorkflowStateException("Validation workflow is inactive");
        }
        if (workflow.getUsage() != ValidationUsage.LEAVE) {
            throw new InvalidWorkflowStateException("Validation workflow usage must be LEAVE");
        }
    }
}
