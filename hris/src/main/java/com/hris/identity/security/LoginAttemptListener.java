package com.hris.identity.security;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.UserRepository;
import com.hris.identity.account.repository.UserCredentialRepository;
import com.hris.tenancy.TenantContext;
import com.hris.tenancy.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Brute-force lockout, replacing Keycloak's built-in protection:
 * {@code max-attempts} consecutive failures lock the account for
 * {@code lock-minutes}; any successful login resets the counter and stamps
 * {@code users.last_login}.
 *
 * <p>Listens only to form-login (password) events — JWT authentications on the
 * API also publish success events and must not touch the counters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptListener {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final AuditLogService auditLogService;

    @Value("${app.auth.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.auth.lockout.lock-minutes:15}")
    private long lockMinutes;

    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        // Failure events carry the raw typed email; the tenant is the ambient
        // login context. Success events carry the composite principal.
        TenantPrincipal principal = TenantPrincipal.parse(event.getAuthentication().getName());
        UUID tenantId = TenantContext.get() != null ? TenantContext.get() : principal.tenantId();

        userRepository.findByTenantIdAndEmail(tenantId, principal.email()).ifPresent(user ->
            userCredentialRepository.findById(user.getId()).ifPresent(credential -> {
                credential.setFailedAttempts(credential.getFailedAttempts() + 1);
                auditLogService.log(user.getId(), AuditAction.LOGIN_FAIL,
                    "user_credentials", user.getId(), null, null);
                if (credential.getFailedAttempts() >= maxAttempts) {
                    credential.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockMinutes)));
                    log.warn("Account {} locked for {} minutes after {} failed login attempts",
                        user.getEmail(), lockMinutes, credential.getFailedAttempts());
                }
                userCredentialRepository.save(credential);
            }));
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        TenantPrincipal principal = TenantPrincipal.parse(event.getAuthentication().getName());

        userRepository.findByTenantIdAndEmail(principal.tenantId(), principal.email()).ifPresent(user -> {
            userCredentialRepository.findById(user.getId()).ifPresent(credential -> {
                if (credential.getFailedAttempts() > 0 || credential.getLockedUntil() != null) {
                    credential.setFailedAttempts(0);
                    credential.setLockedUntil(null);
                    userCredentialRepository.save(credential);
                }
            });
            user.updateLastLogin();
            userRepository.save(user);
            auditLogService.log(user.getId(), AuditAction.LOGIN,
                "user_credentials", user.getId(), null, null);
        });
    }

}
