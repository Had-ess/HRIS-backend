package com.hris.approval.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalStepControllerTest {

    @Test
    @DisplayName("controller requires authenticated access")
    void controllerRequiresAuthenticatedAccess() {
        PreAuthorize preAuthorize = ApprovalStepController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("isAuthenticated()");
    }
}
