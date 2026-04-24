package com.hris.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class KeycloakProvisioningException extends RuntimeException {

    private final HttpStatus responseStatus;
    private final String operation;
    private final HttpStatus keycloakStatus;
    private final String keycloakResponseBody;

    public KeycloakProvisioningException(
            HttpStatus responseStatus,
            String message,
            String operation,
            HttpStatus keycloakStatus,
            String keycloakResponseBody,
            Throwable cause) {
        super(message, cause);
        this.responseStatus = responseStatus;
        this.operation = operation;
        this.keycloakStatus = keycloakStatus;
        this.keycloakResponseBody = keycloakResponseBody;
    }
}
