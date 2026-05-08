package com.hris.auth.service;

import com.hris.auth.dto.KeycloakAdminUserCreateRequest;
import com.hris.common.exception.KeycloakProvisioningException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${keycloak.admin.server-url:http://localhost:8180}")
    private String serverUrl;

    @Value("${keycloak.admin.realm:hris}")
    private String realm;

    @Value("${keycloak.admin.client-id:}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:}")
    private String clientSecret;

    public String createUser(KeycloakAdminUserCreateRequest request) {
        String accessToken = obtainAccessToken();

        URI location;
        try {
            location = restClient.post()
                .uri(serverUrl + "/admin/realms/" + realm + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildCreateUserBody(request))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw mapCreateUserFailure(clientResponse.getStatusCode(), readBody(clientResponse));
                })
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        } catch (ResourceAccessException ex) {
            throw unavailable("create user", "Keycloak is unavailable. Please retry later.", ex);
        }

        String userId = extractUserId(location);
        try {
            setPassword(accessToken, userId, request.password(), request.temporaryPassword());
            return userId;
        } catch (KeycloakProvisioningException ex) {
            rollbackCreatedUser(accessToken, userId, ex);
            throw ex;
        } catch (RuntimeException ex) {
            rollbackCreatedUser(accessToken, userId, ex);
            throw ex;
        }
    }

    public void deleteUser(String userId) {
        String accessToken = obtainAccessToken();
        deleteUser(accessToken, userId);
    }

    public void updateUserProfile(String userId, String email, String firstName, String lastName) {
        String accessToken = obtainAccessToken();
        Map<String, Object> currentUser = getUserRepresentation(accessToken, userId);
        currentUser.put("email", email);
        currentUser.put("firstName", firstName);
        currentUser.put("lastName", lastName);

        try {
            restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(currentUser)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw mapOperationFailure(
                        "update user profile",
                        clientResponse.getStatusCode(),
                        readBody(clientResponse)
                    );
                })
                .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw unavailable("update user profile", "Keycloak is unavailable. Please retry later.", ex);
        }
    }

    private void deleteUser(String accessToken, String userId) {
        restClient.delete()
            .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                throw mapOperationFailure(
                    "delete user",
                    clientResponse.getStatusCode(),
                    readBody(clientResponse)
                );
            })
            .toBodilessEntity();
    }

    private Map<String, Object> getUserRepresentation(String accessToken, String userId) {
        try {
            String response = restClient.get()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw mapOperationFailure(
                        "get user profile",
                        clientResponse.getStatusCode(),
                        readBody(clientResponse)
                    );
                })
                .body(String.class);

            return objectMapper.readValue(response, MAP_TYPE);
        } catch (KeycloakProvisioningException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw unavailable("get user profile", "Keycloak is unavailable. Please retry later.", ex);
        } catch (IOException ex) {
            throw new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak returned an invalid user profile response.",
                "get user profile",
                HttpStatus.OK,
                null,
                ex
            );
        }
    }

    private void setPassword(String accessToken, String userId, String password, boolean temporary) {
        try {
            restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "type", "password",
                    "value", password,
                    "temporary", temporary
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw mapOperationFailure(
                        "set password",
                        clientResponse.getStatusCode(),
                        readBody(clientResponse)
                    );
                })
                .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw unavailable("set password", "Keycloak is unavailable. Please retry later.", ex);
        }
    }

    private String obtainAccessToken() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.error(
                "Keycloak admin client credentials are blank. serverUrl={}, realm={}, clientIdPresent={}, clientSecretPresent={}",
                serverUrl,
                realm,
                clientId != null && !clientId.isBlank(),
                clientSecret != null && !clientSecret.isBlank()
            );
            throw new KeycloakProvisioningException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Keycloak admin client credentials are not configured.",
                "obtain access token",
                null,
                null,
                null
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            String response = restClient.post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw mapTokenFailure(clientResponse.getStatusCode(), readBody(clientResponse));
                })
                .body(String.class);

            Map<String, Object> body = objectMapper.readValue(response, MAP_TYPE);

            Object token = body.get("access_token");
            if (!(token instanceof String accessToken) || accessToken.isBlank()) {
                throw new KeycloakProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "Keycloak admin authentication returned an invalid response.",
                    "obtain access token",
                    HttpStatus.OK,
                    response,
                    null
                );
            }
            return accessToken;
        } catch (KeycloakProvisioningException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw unavailable("obtain access token", "Keycloak is unavailable. Please retry later.", ex);
        } catch (IOException ex) {
            throw new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin authentication returned an unreadable response.",
                "obtain access token",
                HttpStatus.OK,
                null,
                ex
            );
        }
    }

    private String extractUserId(URI location) {
        if (location == null || location.getPath() == null || location.getPath().isBlank()) {
            throw new IllegalStateException("Keycloak user creation did not return a valid location");
        }

        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            throw new IllegalStateException("Unable to extract created Keycloak user ID");
        }
        return path.substring(slash + 1);
    }

    private void rollbackCreatedUser(String accessToken, String userId, RuntimeException cause) {
        try {
            deleteUser(accessToken, userId);
        } catch (RuntimeException rollbackEx) {
            log.warn("Failed to rollback created Keycloak user {} after {} failure", userId, classifyOperation(cause), rollbackEx);
        }
    }

    private Map<String, Object> buildCreateUserBody(KeycloakAdminUserCreateRequest request) {
        return Map.of(
            "username", request.username(),
            "email", request.email(),
            "firstName", request.firstName(),
            "lastName", request.lastName(),
            "enabled", true,
            "requiredActions", request.temporaryPassword() ? List.of("UPDATE_PASSWORD") : List.of()
        );
    }

    static String resolveCreateConflictMessage(String responseBody) {
        String normalized = normalizeBody(responseBody);
        boolean usernameConflict = normalized.contains("username");
        boolean emailConflict = normalized.contains("email");

        if (usernameConflict && emailConflict) {
            return "A Keycloak user with this username or email already exists";
        }
        if (usernameConflict) {
            return "A Keycloak user with this username already exists";
        }
        if (emailConflict) {
            return "A Keycloak user with this email already exists";
        }
        return "Keycloak reported a conflicting existing user";
    }

    private KeycloakProvisioningException mapCreateUserFailure(HttpStatusCode statusCode, String responseBody) {
        HttpStatus keycloakStatus = HttpStatus.resolve(statusCode.value());
        if (statusCode.value() == HttpStatus.CONFLICT.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.CONFLICT,
                resolveCreateConflictMessage(responseBody),
                "create user",
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_REQUEST,
                "Keycloak rejected the user details. Please verify username, email, and required fields.",
                "create user",
                keycloakStatus,
                responseBody,
                null
            );
        }
        return mapOperationFailure("create user", statusCode, responseBody);
    }

    private KeycloakProvisioningException mapTokenFailure(HttpStatusCode statusCode, String responseBody) {
        HttpStatus keycloakStatus = HttpStatus.resolve(statusCode.value());
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            String normalized = normalizeBody(responseBody);
            if (normalized.contains("unauthorized_client")) {
                return new KeycloakProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "Keycloak admin client is not authorized for client credentials. Enable service accounts for the client.",
                    "obtain access token",
                    keycloakStatus,
                    responseBody,
                    null
                );
            }
            if (normalized.contains("invalid_client")) {
                return new KeycloakProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "Keycloak admin client authentication failed. Verify the configured client ID and secret.",
                    "obtain access token",
                    keycloakStatus,
                    responseBody,
                    null
                );
            }
            return new KeycloakProvisioningException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Keycloak admin client configuration is invalid.",
                "obtain access token",
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin authentication failed. Contact support.",
                "obtain access token",
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin client is not allowed to obtain admin access.",
                "obtain access token",
                keycloakStatus,
                responseBody,
                null
            );
        }
        return mapOperationFailure("obtain access token", statusCode, responseBody);
    }

    private KeycloakProvisioningException mapOperationFailure(
            String operation,
            HttpStatusCode statusCode,
            String responseBody) {
        HttpStatus keycloakStatus = HttpStatus.resolve(statusCode.value());

        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin authentication failed. Contact support.",
                operation,
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin permissions are insufficient for account provisioning.",
                operation,
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.BAD_REQUEST,
                "Keycloak rejected the account provisioning request.",
                operation,
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.is5xxServerError()) {
            return new KeycloakProvisioningException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Keycloak is unavailable. Please retry later.",
                operation,
                keycloakStatus,
                responseBody,
                null
            );
        }
        if (statusCode.value() == HttpStatus.CONFLICT.value()) {
            return new KeycloakProvisioningException(
                HttpStatus.CONFLICT,
                "Keycloak reported a conflicting account state.",
                operation,
                keycloakStatus,
                responseBody,
                null
            );
        }

        return new KeycloakProvisioningException(
            HttpStatus.BAD_GATEWAY,
            "Keycloak account provisioning failed.",
            operation,
            keycloakStatus,
            responseBody,
            null
        );
    }

    private KeycloakProvisioningException unavailable(String operation, String message, Throwable cause) {
        return new KeycloakProvisioningException(
            HttpStatus.SERVICE_UNAVAILABLE,
            message,
            operation,
            null,
            null,
            cause
        );
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) throws IOException {
        byte[] bytes = response.getBody().readAllBytes();
        return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalizeBody(String responseBody) {
        return responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
    }

    private String classifyOperation(RuntimeException cause) {
        if (cause instanceof KeycloakProvisioningException ex) {
            return ex.getOperation();
        }
        return cause.getClass().getSimpleName();
    }
}
