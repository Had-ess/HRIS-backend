package com.hris.approval.mapper;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T18:12:20+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class ApprovalMapperImpl implements ApprovalMapper {

    @Override
    public ApprovalStepResponseDto toStepDto(ApprovalStep step) {
        if ( step == null ) {
            return null;
        }

        UUID id = null;
        UUID workflowId = null;
        UUID approverId = null;
        int stepOrder = 0;
        StepStatus status = null;
        ApprovalContext context = null;
        String comment = null;
        Instant decidedAt = null;

        id = step.getId();
        workflowId = step.getWorkflowId();
        approverId = step.getApproverId();
        stepOrder = step.getStepOrder();
        status = step.getStatus();
        context = step.getContext();
        comment = step.getComment();
        decidedAt = step.getDecidedAt();

        ApprovalStepResponseDto approvalStepResponseDto = new ApprovalStepResponseDto( id, workflowId, approverId, stepOrder, status, context, comment, decidedAt );

        return approvalStepResponseDto;
    }

    @Override
    public List<ApprovalStepResponseDto> toStepDtoList(List<ApprovalStep> steps) {
        if ( steps == null ) {
            return null;
        }

        List<ApprovalStepResponseDto> list = new ArrayList<ApprovalStepResponseDto>( steps.size() );
        for ( ApprovalStep approvalStep : steps ) {
            list.add( toStepDto( approvalStep ) );
        }

        return list;
    }
}
