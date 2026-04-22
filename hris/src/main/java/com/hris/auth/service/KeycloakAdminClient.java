package com.hris.auth.service;

import com.hris.auth.dto.KeycloakAdminUserCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

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

        URI location = restClient.post()
            .uri(serverUrl + "/admin/realms/" + realm + "/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "username", request.username(),
                "email", request.email(),
                "firstName", request.firstName(),
                "lastName", request.lastName(),
                "enabled", true
            ))
            .retrieve()
            .toBodilessEntity()
            .getHeaders()
            .getLocation();

        String userId = extractUserId(location);
        setPassword(accessToken, userId, request.password(), request.temporaryPassword());
        assignRealmRoles(accessToken, userId, request.realmRoles());
        return userId;
    }

    public void deleteUser(String userId) {
        String accessToken = obtainAccessToken();
        restClient.delete()
            .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .toBodilessEntity();
    }

    private void setPassword(String accessToken, String userId, String password, boolean temporary) {
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
            .toBodilessEntity();
    }

    private void assignRealmRoles(String accessToken, String userId, List<String> roleNames) {
        List<Map<String, Object>> roles = roleNames.stream()
            .map(roleName -> getRealmRole(accessToken, roleName))
            .toList();

        restClient.post()
            .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(roles)
            .retrieve()
            .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRealmRole(String accessToken, String roleName) {
        return restClient.get()
            .uri(serverUrl + "/admin/realms/" + realm + "/roles/" + roleName)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private String obtainAccessToken() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Keycloak admin client credentials are not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            Map<String, Object> response = restClient.post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

            Object token = response != null ? response.get("access_token") : null;
            if (!(token instanceof String accessToken) || accessToken.isBlank()) {
                throw new IllegalStateException("Keycloak admin access token response was invalid");
            }
            return accessToken;
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException("Unable to authenticate with Keycloak Admin API", ex);
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
}
