package com.hris.admin.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminRequest Entity Unit Tests")
class AdminRequestTest {

    @Test
    @DisplayName("generateRequestNumber should produce AR-yyyyMMdd-xxxxx format")
    void shouldGenerateRequestNumberInCorrectFormat() {
        String requestNumber = AdminRequest.generateRequestNumber();

        assertThat(requestNumber).startsWith("AR-");
        assertThat(requestNumber).matches("AR-\\d{8}-\\d{5}");
    }

    @Test
    @DisplayName("generateRequestNumber should produce format consistently")
    void shouldGenerateRequestNumberConsistently() {
        String first = AdminRequest.generateRequestNumber();
        String second = AdminRequest.generateRequestNumber();

        assertThat(first).matches("AR-\\d{8}-\\d{5}");
        assertThat(second).matches("AR-\\d{8}-\\d{5}");
    }
}
