package com.hris.identity.account;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.entity.UserActionToken;
import com.hris.identity.account.repository.UserActionTokenRepository;
import com.hris.identity.account.repository.UserCredentialRepository;
import com.hris.tenancy.TenantContext;
import com.hris.tenancy.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Local account lifecycle — the replacement for {@code KeycloakAdminClient}.
 * All operations are plain database writes, so callers get single-transaction
 * atomicity instead of the old create-then-compensate dance against Keycloak.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalAccountService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserActionTokenRepository userActionTokenRepository;
    private final CredentialService credentialService;
    private final ActionTokenService actionTokenService;
    private final AccountEmailService accountEmailService;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Issues an activation token for a freshly provisioned user. The token row
     * commits with the caller's transaction; the email goes out only after
     * commit and is best-effort (a failed send never fails provisioning —
     * HR can re-send from the employee page).
     */
    @Transactional
    public void initiateActivation(User user) {
        String rawToken = actionTokenService.issue(
            user.getId(), UserActionToken.Purpose.ACTIVATION, ActionTokenService.ACTIVATION_TTL);
        sendAfterCommit(() ->
            accountEmailService.sendActivationEmail(user.getEmail(), displayName(user), rawToken));
    }

    @Transactional(readOnly = true)
    public boolean isActivated(UUID userId) {
        return userCredentialRepository.existsById(userId);
    }

    /**
     * Anonymous flow: no ambient tenant context exists when an activation
     * link is opened. The token row (RLS-exempt, guarded by its 256-bit hash)
     * carries the tenant; the actual account mutation runs in a fresh
     * transaction inside that tenant's context. A new transaction is required
     * because the RLS setting binds at connection checkout — joining an
     * already-open transaction would keep the contextless connection.
     */
    public void activate(String rawToken, String newPassword) {
        UserActionToken token = actionTokenService.consume(rawToken, UserActionToken.Purpose.ACTIVATION);
        runInTenant(tokenTenant(token), () -> {
            UUID userId = token.getUserId();
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

            credentialService.setPassword(userId, newPassword);
            user.setSeed(false);
            userRepository.save(user);

            auditLogService.log(userId, AuditAction.UPDATE, "user_credentials", userId, null, null);
            log.info("Account activated for user {}", user.getEmail());
        });
    }

    /**
     * Always silent: callers return the same response whether or not the email
     * matches an account, so this endpoint cannot be used for user enumeration.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.trim().toLowerCase())
            .filter(User::isActive)
            .filter(user -> credentialService.hasCredentials(user.getId()))
            .ifPresent(user -> {
                String rawToken = actionTokenService.issue(
                    user.getId(), UserActionToken.Purpose.PASSWORD_RESET, ActionTokenService.PASSWORD_RESET_TTL);
                sendAfterCommit(() ->
                    accountEmailService.sendPasswordResetEmail(user.getEmail(), displayName(user), rawToken));
            });
    }

    /** Anonymous flow — same tenant-from-token pattern as {@link #activate}. */
    public void resetPassword(String rawToken, String newPassword) {
        UserActionToken token = actionTokenService.consume(rawToken, UserActionToken.Purpose.PASSWORD_RESET);
        runInTenant(tokenTenant(token), () -> {
            UUID userId = token.getUserId();
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

            credentialService.setPassword(userId, newPassword);
            revokeAllSessions(user);

            auditLogService.log(userId, AuditAction.UPDATE, "user_credentials", userId, null, null);
            log.info("Password reset completed for user {}", user.getEmail());
        });
    }

    /**
     * @param currentAccessToken the caller's raw access token; its
     *     authorization survives so the client can finish with a proper
     *     RP-initiated logout (the OIDC logout endpoint validates
     *     id_token_hint against the stored authorization). All other
     *     sessions are revoked.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword,
                               String currentAccessToken) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!credentialService.verify(userId, currentPassword)) {
            throw new AccessDeniedException("CURRENT_PASSWORD_INVALID");
        }

        credentialService.setPassword(userId, newPassword);
        revokeOtherSessions(user, currentAccessToken);

        auditLogService.log(userId, AuditAction.UPDATE, "user_credentials", userId, null, null);
    }

    /**
     * Removes credentials, outstanding tokens, and server-side sessions.
     * Called before the user row itself is deleted or archived.
     */
    @Transactional
    public void deleteAccount(User user) {
        userActionTokenRepository.deleteByUserId(user.getId());
        userCredentialRepository.deleteById(user.getId());
        revokeAllSessions(user);
    }

    public void revokeAllSessions(User user) {
        String principal = principalOf(user);
        int revoked = jdbcTemplate.update(
            "DELETE FROM oauth2_authorization WHERE principal_name = ?", principal);
        int formSessions = deleteFormLoginSessions(principal);
        if (revoked > 0 || formSessions > 0) {
            log.info("Revoked {} authorization(s) and {} login session(s) for {}",
                revoked, formSessions, user.getEmail());
        }
    }

    /**
     * Revokes every authorization except the one backing the given access
     * token, plus ALL form-login sessions (they cannot be tied to a single
     * authorization; every device — including this one — re-authenticates).
     */
    public void revokeOtherSessions(User user, String currentAccessToken) {
        if (currentAccessToken == null || currentAccessToken.isBlank()) {
            revokeAllSessions(user);
            return;
        }
        String principal = principalOf(user);
        int revoked = jdbcTemplate.update(
            "DELETE FROM oauth2_authorization WHERE principal_name = ? "
                + "AND (access_token_value IS NULL OR access_token_value <> ?)",
            principal, currentAccessToken);
        int formSessions = deleteFormLoginSessions(principal);
        if (revoked > 0 || formSessions > 0) {
            log.info("Revoked {} other authorization(s) and {} login session(s) for {}",
                revoked, formSessions, user.getEmail());
        }
    }

    /**
     * Session/authorization stores key on the composite tenant principal
     * (see TenantPrincipal). tenant_id may be null for entities created
     * before V62 in stale unit-test fixtures — default tenant then.
     */
    private String principalOf(User user) {
        UUID tenantId = user.getTenantId() != null
            ? user.getTenantId()
            : TenantContext.DEFAULT_TENANT_ID;
        return new TenantPrincipal(tenantId, user.getEmail()).format();
    }

    private UUID tokenTenant(UserActionToken token) {
        return token.getTenantId() != null ? token.getTenantId() : TenantContext.DEFAULT_TENANT_ID;
    }

    /** Fresh transaction inside the tenant context (RLS binds at checkout). */
    private void runInTenant(UUID tenantId, Runnable action) {
        TenantContext.runAs(tenantId, () -> transactionTemplate.executeWithoutResult(status -> action.run()));
    }

    /**
     * Spring Session JDBC rows back the authorization server's form login;
     * deleting them ends those sessions on the next request (attributes
     * cascade via FK).
     */
    private int deleteFormLoginSessions(String principal) {
        return jdbcTemplate.update(
            "DELETE FROM spring_session WHERE principal_name = ?", principal);
    }

    private String displayName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private void sendAfterCommit(Runnable mailAction) {
        Runnable safeAction = () -> {
            try {
                mailAction.run();
            } catch (Exception ex) {
                // Best-effort: provisioning/reset must not fail on SMTP issues.
                log.warn("Account email could not be sent", ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
        } else {
            safeAction.run();
        }
    }
}
