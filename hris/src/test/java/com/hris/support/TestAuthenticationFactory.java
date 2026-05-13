package com.hris.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

public final class TestAuthenticationFactory {

    private TestAuthenticationFactory() {
    }

    public static JwtAuthenticationToken jwtAuthentication(UUID userId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("local_user_id", userId.toString())
            .build();

        return new JwtAuthenticationToken(
            jwt,
            List.of(roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList(),
            userId.toString()
        );
    }

    public static RequestPostProcessor jwtRequest(UUID userId, String... roles) {
        return request -> {
            request.setUserPrincipal(jwtAuthentication(userId, roles));
            return request;
        };
    }
}
