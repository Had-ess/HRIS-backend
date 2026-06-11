package com.hris.tenancy;

import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Platform-operator API for tenant provisioning. Guarded by the
 * PLATFORM_ADMIN permission, which only the default tenant's operator
 * profile holds (V68) — customer-tenant admins never receive it.
 */
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'PLATFORM', 'ADMIN')")
public class PlatformTenantController {

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantRepository tenantRepository;

    public record TenantCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
            message = "lowercase letters, digits and hyphens only")
        String slug,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email String adminEmail,
        @NotBlank @Size(max = 100) String adminFirstName,
        @NotBlank @Size(max = 100) String adminLastName
    ) {
    }

    public record TenantResponse(UUID id, String slug, String name, String status, Instant createdAt) {
        static TenantResponse of(Tenant tenant) {
            return new TenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getStatus(), tenant.getCreatedAt());
        }
    }

    public record TenantProvisionResponse(UUID id, String slug, String name, String status, UUID adminUserId) {
    }

    @GetMapping
    public List<TenantResponse> list() {
        return tenantRepository.findAll().stream().map(TenantResponse::of).toList();
    }

    @PostMapping
    public ResponseEntity<TenantProvisionResponse> create(@Valid @RequestBody TenantCreateRequest request,
                                                          Authentication authentication) {
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        TenantProvisioningService.ProvisionResult result = tenantProvisioningService.create(
            request.slug(), request.name(), request.adminEmail(),
            request.adminFirstName(), request.adminLastName(), actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TenantProvisionResponse(
            result.tenantId(), result.slug(), result.name(), result.status(), result.adminUserId()));
    }
}
