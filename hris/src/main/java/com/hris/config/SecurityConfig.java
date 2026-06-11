package com.hris.config;

import com.hris.security.DbRbacJwtConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless API security chain (order 3). Orders 1 and 2 — the embedded
 * authorization server's protocol endpoints and its form login — live in
 * {@link com.hris.identity.authserver.AuthorizationServerConfig}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String NOTIFICATION_STREAM_PATH = "/api/notifications/stream";

    private final DbRbacJwtConverter dbRbacJwtConverter;

    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    @Order(3)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("localJwtDecoder") JwtDecoder localJwtDecoder) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Anonymous-by-nature account flows (activation links, password reset)
                .requestMatchers(
                    "/api/auth/activate",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt
                    .decoder(localJwtDecoder)
                    .jwtAuthenticationConverter(dbRbacJwtConverter))
            );

        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: blob:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'"))
            .referrerPolicy(ref -> ref.policy(
                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .permissionsPolicy(perm -> perm.policy(
                "geolocation=(), camera=(), microphone=(), payment=(), usb=()"))
        );

        return http.build();
    }

    /**
     * Standard header-based bearer resolution, plus a query-parameter fallback restricted
     * to the SSE stream endpoint — the browser EventSource API cannot set headers.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        return request -> {
            String token = headerResolver.resolve(request);
            if (token == null && NOTIFICATION_STREAM_PATH.equals(request.getRequestURI())) {
                String fromQuery = request.getParameter("access_token");
                if (fromQuery != null && !fromQuery.isBlank()) {
                    return fromQuery;
                }
            }
            return token;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Accept-Language",
            "Origin",
            "X-Requested-With",
            "X-Request-ID"
        ));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
