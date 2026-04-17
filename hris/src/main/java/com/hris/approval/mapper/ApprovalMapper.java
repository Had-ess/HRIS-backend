package com.hris.approval.mapper;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.dto.ApprovalWorkflowResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ApprovalMapper {

    ApprovalStepResponseDto toStepDto(ApprovalStep step);

    List<ApprovalStepResponseDto> toStepDtoList(List<ApprovalStep> steps);

    default ApprovalWorkflowResponseDto toWorkflowDto(ApprovalWorkflow workflow, List<ApprovalStep> steps) {
        return new ApprovalWorkflowResponseDto(
            workflow.getId(),
            workflow.getSubjectType(),
            workflow.getSubjectId(),
            workflow.getStatus(),
            workflow.getCreatedAt(),
            workflow.getCompletedAt(),
            toStepDtoList(steps)
        );
    }
}
