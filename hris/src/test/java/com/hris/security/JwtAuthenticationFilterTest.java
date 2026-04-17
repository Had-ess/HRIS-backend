package com.hris.security;

import com.hris.auth.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private UserProvisioningService userProvisioningService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("provisioning failure returns 401 and stops filter chain")
    void provisioningFailureReturnsUnauthorized() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(userProvisioningService);
        Jwt jwt = buildJwt();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        doThrow(new IllegalStateException("provisioning failed"))
            .when(userProvisioningService).resolveUserId(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/leave-requests");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getErrorMessage()).isEqualTo("Authentication provisioning failed");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("valid provisioning enriches authentication and continues filter chain")
    void validProvisioningContinuesFilterChain() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(userProvisioningService);
        Jwt jwt = buildJwt();
        UUID localUserId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userProvisioningService.resolveUserId(jwt)).thenReturn(localUserId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/leave-requests");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isInstanceOf(JwtAuthenticationToken.class);

        JwtAuthenticationToken authentication =
            (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getToken().getClaimAsString("local_user_id"))
            .isEqualTo(localUserId.toString());
        assertThat(authentication.getName()).isEqualTo(localUserId.toString());
    }

    private Jwt buildJwt() {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "keycloak-user")
            .claim("email", "user@example.com")
            .build();
    }
}
