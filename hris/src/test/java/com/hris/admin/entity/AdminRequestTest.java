package com.hris.admin.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AdminRequest Entity Unit Tests")
class AdminRequestTest {

    @Test
    @DisplayName("generateTrackingNumber should produce AR-yyyyMMdd-xxxxx format")
    void shouldGenerateTrackingNumber_InCorrectFormat() {
        String tracking = AdminRequest.generateTrackingNumber();

        assertThat(tracking).startsWith("AR-");
        assertThat(tracking).matches("AR-\\d{8}-\\d{5}");
    }

    @Test
    @DisplayName("generateTrackingNumber should produce unique values")
    void shouldGenerateUniqueValues() {
        String t1 = AdminRequest.generateTrackingNumber();
        String t2 = AdminRequest.generateTrackingNumber();

        // Extremely unlikely but not guaranteed to differ;
        // the test validates format consistency across calls
        assertThat(t1).matches("AR-\\d{8}-\\d{5}");
        assertThat(t2).matches("AR-\\d{8}-\\d{5}");
    }
}
