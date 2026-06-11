package com.hris.identity.web;

import com.hris.common.ApiResponse;
import com.hris.identity.account.LocalAccountService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Account self-service flows: activation (from onboarding email), password
 * reset, and authenticated password change. Activation and reset are
 * anonymous by nature and explicitly permitted in SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthFlowController {

    private final LocalAccountService localAccountService;

    public record ActivateRequest(@NotBlank String token, @NotBlank String password) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String password) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@Valid @RequestBody ActivateRequest request) {
        localAccountService.activate(request.token(), request.password());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Always 202 regardless of whether the email exists — prevents user
     * enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        localAccountService.requestPasswordReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        localAccountService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        String currentAccessToken = auth instanceof JwtAuthenticationToken jwtAuth
            ? jwtAuth.getToken().getTokenValue()
            : null;
        localAccountService.changePassword(
            userId, request.currentPassword(), request.newPassword(), currentAccessToken);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
