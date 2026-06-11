package com.hris.identity.authserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges finished rows from {@code oauth2_authorization}. Every login and
 * every silent renew creates a row (one per authorization-code grant, roughly
 * every 4.5 minutes per open tab) and Spring Authorization Server never
 * deletes them, so without this job the table grows without bound.
 *
 * <p>A row is purged once every token it carries has been expired for at
 * least an hour — long enough that no flow (including RP-initiated logout
 * with id_token_hint) can still reference it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthorizationPurgeJob {

    private static final String PURGE_SQL = """
        DELETE FROM oauth2_authorization
        WHERE GREATEST(
            COALESCE(authorization_code_expires_at, 'epoch'::timestamptz),
            COALESCE(access_token_expires_at,       'epoch'::timestamptz),
            COALESCE(oidc_id_token_expires_at,      'epoch'::timestamptz),
            COALESCE(refresh_token_expires_at,      'epoch'::timestamptz),
            COALESCE(user_code_expires_at,          'epoch'::timestamptz),
            COALESCE(device_code_expires_at,        'epoch'::timestamptz)
        ) < now() - interval '1 hour'
        """;

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${app.auth.authorization-purge.cron:0 30 * * * *}")
    @SchedulerLock(name = "oauth2AuthorizationPurgeJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void purgeExpiredAuthorizations() {
        int purged = jdbcTemplate.update(PURGE_SQL);
        if (purged > 0) {
            log.info("Purged {} expired oauth2_authorization row(s)", purged);
        }
    }
}
