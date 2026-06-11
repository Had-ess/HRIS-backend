package com.hris.identity.authserver;

import com.hris.identity.security.AuthRateLimitFilter;
import com.hris.tenancy.LoginTenantFilter;
import com.hris.tenancy.TenantResolver;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;
import java.util.Map;

/**
 * Embedded OAuth2/OIDC authorization server (Spring Authorization Server).
 * Issues the RS256 JWTs the existing resource-server layer consumes — the
 * replacement for Keycloak. See docs/AUTH_MIGRATION_DESIGN.md.
 */
@Configuration
public class AuthorizationServerConfig {

    @Value("${app.auth.issuer:http://localhost:8081}")
    private String issuer;

    @Value("${app.auth.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /**
     * Protocol endpoints (/oauth2/authorize, /oauth2/token, /oauth2/jwks,
     * OIDC discovery, /connect/logout). Browser-originated token calls come
     * from the Angular origin, hence CORS; unauthenticated HTML hits redirect
     * to the custom login page.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            TenantResolver tenantResolver) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
            .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .with(authorizationServerConfigurer, authorizationServer ->
                authorizationServer.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // Remembers ?tenant= from /oauth2/authorize in the session so the
            // /login redirect renders the right tenant (no-subdomain dev mode),
            // and gives the token endpoint's claim customization its context.
            .addFilterBefore(new LoginTenantFilter(tenantResolver), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Session-based form login backing the authorization server. Only /login
     * and /logout live here; everything else stays on the stateless API chain.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain loginSecurityFilterChain(
            HttpSecurity http, TenantResolver tenantResolver) throws Exception {
        http
            .securityMatcher("/login", "/logout")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .formLogin(form -> form.loginPage("/login").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            // Tenant the visitor signs in to: form field / subdomain / default
            .addFilterBefore(new LoginTenantFilter(tenantResolver), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient frontend = RegisteredClient.withId("hris-frontend")
            .clientId("hris-frontend")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            // No REFRESH_TOKEN grant: SAS refuses refresh tokens to public
            // clients (gh-297); the SPA renews via prompt=none silent iframe.
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(frontendUrl + "/auth/callback")
            .redirectUri(frontendUrl + "/assets/silent-renew.html")
            .postLogoutRedirectUri(frontendUrl)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .clientSettings(ClientSettings.builder()
                .requireProofKey(true)                 // PKCE mandatory for the public SPA client
                .requireAuthorizationConsent(false)    // first-party app: no consent screen
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(frontend);
    }

    /**
     * JDBC-backed so refresh tokens survive restarts and are revocable
     * (LocalAccountService.revokeAllSessions deletes by principal name).
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JwkKeyService jwkKeyService) {
        JWKSet jwkSet = jwkKeyService.loadOrCreateJwkSet();
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * Decoder for our tokens, built straight from the local JWKSource — no
     * HTTP round-trip to ourselves. The API resource-server chain uses it.
     */
    @Bean
    public JwtDecoder localJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtDecoder decoder =
            (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Registered ahead of the Spring Security chain so throttled requests are
     * rejected before any credential processing.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration() {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
            new FilterRegistrationBean<>(new AuthRateLimitFilter());
        registration.addUrlPatterns("/login", "/oauth2/token", "/api/auth/*");
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating with bcrypt-12 default: hashes carry {bcrypt}, so a future
        // Argon2id upgrade is a config change, not a data migration.
        return new DelegatingPasswordEncoder("bcrypt",
            Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
