package com.hris.identity.account;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.identity.account.entity.UserActionToken;
import com.hris.identity.account.repository.UserActionTokenRepository;
import com.hris.identity.account.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Transactional
    public void activate(String rawToken, String newPassword) {
        UUID userId = actionTokenService.consume(rawToken, UserActionToken.Purpose.ACTIVATION);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        credentialService.setPassword(userId, newPassword);
        user.setSeed(false);
        userRepository.save(user);

        auditLogService.log(userId, AuditAction.UPDATE, "user_credentials", userId, null, null);
        log.info("Account activated for user {}", user.getEmail());
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

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = actionTokenService.consume(rawToken, UserActionToken.Purpose.PASSWORD_RESET);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        credentialService.setPassword(userId, newPassword);
        revokeAllSessions(user.getEmail());

        auditLogService.log(userId, AuditAction.UPDATE, "user_credentials", userId, null, null);
        log.info("Password reset completed for user {}", user.getEmail());
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
        revokeOtherSessions(user.getEmail(), currentAccessToken);

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
        revokeAllSessions(user.getEmail());
    }

    public void revokeAllSessions(String email) {
        int revoked = jdbcTemplate.update(
            "DELETE FROM oauth2_authorization WHERE principal_name = ?", email);
        int formSessions = deleteFormLoginSessions(email);
        if (revoked > 0 || formSessions > 0) {
            log.info("Revoked {} authorization(s) and {} login session(s) for {}",
                revoked, formSessions, email);
        }
    }

    /**
     * Revokes every authorization except the one backing the given access
     * token, plus ALL form-login sessions (they cannot be tied to a single
     * authorization; every device — including this one — re-authenticates).
     */
    public void revokeOtherSessions(String email, String currentAccessToken) {
        if (currentAccessToken == null || currentAccessToken.isBlank()) {
            revokeAllSessions(email);
            return;
        }
        int revoked = jdbcTemplate.update(
            "DELETE FROM oauth2_authorization WHERE principal_name = ? "
                + "AND (access_token_value IS NULL OR access_token_value <> ?)",
            email, currentAccessToken);
        int formSessions = deleteFormLoginSessions(email);
        if (revoked > 0 || formSessions > 0) {
            log.info("Revoked {} other authorization(s) and {} login session(s) for {}",
                revoked, formSessions, email);
        }
    }

    /**
     * Spring Session JDBC rows back the authorization server's form login;
     * deleting them ends those sessions on the next request (attributes
     * cascade via FK).
     */
    private int deleteFormLoginSessions(String email) {
        return jdbcTemplate.update(
            "DELETE FROM spring_session WHERE principal_name = ?", email);
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
