package com.hris.common;

import com.hris.common.exception.KeycloakProvisioningException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("returns the safe Keycloak provisioning response status and message")
    void returnsSafeKeycloakProvisioningResponse() {
        KeycloakProvisioningException exception = new KeycloakProvisioningException(
            HttpStatus.BAD_GATEWAY,
            "Keycloak admin authentication failed. Contact support.",
            "obtain access token",
            HttpStatus.UNAUTHORIZED,
            "{\"error\":\"unauthorized_client\"}",
            null
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleKeycloakProvisioning(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
            .isEqualTo("Keycloak admin authentication failed. Contact support.");
    }
}
