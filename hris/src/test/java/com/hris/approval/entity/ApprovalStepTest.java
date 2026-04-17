package com.hris.approval.entity;

import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ApprovalStep Entity Unit Tests")
class ApprovalStepTest {

    @Test
    @DisplayName("isPending should return true when status is PENDING")
    void shouldReturnTrue_WhenPending() {
        ApprovalStep step = ApprovalStep.builder()
            .status(StepStatus.PENDING)
            .build();

        assertThat(step.isPending()).isTrue();
    }

    @Test
    @DisplayName("isPending should return false when status is APPROVED")
    void shouldReturnFalse_WhenApproved() {
        ApprovalStep step = ApprovalStep.builder()
            .status(StepStatus.APPROVED)
            .build();

        assertThat(step.isPending()).isFalse();
    }

    @Test
    @DisplayName("isPending should return false when status is REJECTED")
    void shouldReturnFalse_WhenRejected() {
        ApprovalStep step = ApprovalStep.builder()
            .status(StepStatus.REJECTED)
            .build();

        assertThat(step.isPending()).isFalse();
    }

    @Test
    @DisplayName("approve should set status to APPROVED with comment and timestamp")
    void shouldSetApprovedState() {
        ApprovalStep step = ApprovalStep.builder()
            .status(StepStatus.PENDING)
            .build();

        step.approve("Looks good");

        assertThat(step.getStatus()).isEqualTo(StepStatus.APPROVED);
        assertThat(step.getComment()).isEqualTo("Looks good");
        assertThat(step.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("reject should set status to REJECTED with comment and timestamp")
    void shouldSetRejectedState() {
        ApprovalStep step = ApprovalStep.builder()
            .status(StepStatus.PENDING)
            .build();

        step.reject("Insufficient justification");

        assertThat(step.getStatus()).isEqualTo(StepStatus.REJECTED);
        assertThat(step.getComment()).isEqualTo("Insufficient justification");
        assertThat(step.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("equality should be based on ID")
    void shouldBeEqualById() {
        UUID id = UUID.randomUUID();

        ApprovalStep s1 = ApprovalStep.builder().id(id).stepOrder(1).build();
        ApprovalStep s2 = ApprovalStep.builder().id(id).stepOrder(2).build();

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    @DisplayName("two steps with null IDs should not be equal")
    void shouldNotBeEqual_WhenBothNull() {
        ApprovalStep s1 = ApprovalStep.builder().stepOrder(1).build();
        ApprovalStep s2 = ApprovalStep.builder().stepOrder(1).build();

        assertThat(s1).isNotEqualTo(s2);
    }
}
