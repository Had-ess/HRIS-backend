package com.hris.identity.authserver;

import com.hris.auth.repository.UserRepository;
import com.hris.tenancy.Tenant;
import com.hris.tenancy.TenantContext;
import com.hris.tenancy.TenantPrincipal;
import com.hris.tenancy.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Shapes issued JWTs to the contract the rest of the system expects:
 * {@code sub} (and the transition-compat {@code local_user_id}) carry the
 * LOCAL users.id; {@code tid}/{@code tenant} carry the tenant; the profile
 * claims stay bare (the composite principal never leaves the server). No
 * roles/permissions in the token — authorization stays DB-resolved per
 * request.
 */
@Component
@RequiredArgsConstructor
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }

        TenantPrincipal principal = TenantPrincipal.parse(context.getPrincipal().getName());
        String tenantSlug = tenantRepository.findById(principal.tenantId())
            .map(Tenant::getSlug)
            .orElse(null);

        // The token endpoint has no ambient tenant context; the user lookup
        // must run inside the principal's tenant for RLS to return the row.
        TenantContext.runAs(principal.tenantId(), () ->
            userRepository.findByTenantIdAndEmail(principal.tenantId(), principal.email())
                .ifPresent(user -> {
                    context.getClaims()
                        .subject(user.getId().toString())
                        .claim("local_user_id", user.getId().toString())
                        .claim("tid", principal.tenantId().toString())
                        .claim("email", user.getEmail())
                        .claim("given_name", user.getFirstName())
                        .claim("family_name", user.getLastName())
                        .claim("locale", user.getLocalePreference());
                    if (tenantSlug != null) {
                        context.getClaims().claim("tenant", tenantSlug);
                    }
                }));
    }
}
