package com.hris.identity.authserver;

import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Shapes issued JWTs to the contract the rest of the system already expects:
 * {@code sub} (and the transition-compat {@code local_user_id}) carry the
 * LOCAL users.id, plus the profile claims the frontend reads. Because the
 * issuer and the user store share a database, no JIT provisioning is needed
 * downstream. No roles/permissions in the token — authorization stays
 * DB-resolved per request.
 */
@Component
@RequiredArgsConstructor
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepository;

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }

        String email = context.getPrincipal().getName();
        userRepository.findByEmail(email).ifPresent(user -> context.getClaims()
            .subject(user.getId().toString())
            .claim("local_user_id", user.getId().toString())
            .claim("email", user.getEmail())
            .claim("given_name", user.getFirstName())
            .claim("family_name", user.getLastName())
            .claim("locale", user.getLocalePreference()));
    }
}
