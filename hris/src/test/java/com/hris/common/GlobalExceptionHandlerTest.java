package com.hris.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("maps IllegalArgumentException to 400 with the message")
    void mapsIllegalArgumentToBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleIllegalArgument(new IllegalArgumentException("PASSWORD_POLICY_TOO_SHORT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("PASSWORD_POLICY_TOO_SHORT");
    }

    @Test
    @DisplayName("maps IllegalStateException to 409 with the message")
    void mapsIllegalStateToConflict() {
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleIllegalState(new IllegalStateException("User email must be unique"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("User email must be unique");
    }
}
