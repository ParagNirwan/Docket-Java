package com.docket.app.service;

import com.docket.app.repository.AccountTokenRepository;
import com.docket.app.repository.EmailOutboxRepository;
import com.docket.app.repository.NotificationRepository;
import com.docket.app.repository.RefreshTokenRepository;
import com.docket.app.repository.TenantInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// Clears out data that has outlived its use. Running on several pods at once is
// harmless, because each job only deletes rows that are already past their cutoff.
@Component
@RequiredArgsConstructor
public class MaintenanceJobs {
    // Matches the floor enforced by the trigger in V10.
    static final int MINIMUM_AUDIT_RETENTION_DAYS = 365;

    private final JdbcTemplate jdbcTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final AuthRateLimiter authRateLimiter;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final NotificationRepository notificationRepository;

    @Value("${docket.audit.retention-days}")
    private int auditRetentionDays;

    // Expired tokens stay for a while, so that replaying one still gets a clear "invalid".
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredAuthData() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        refreshTokenRepository.deleteExpiredBefore(cutoff);
        accountTokenRepository.deleteExpiredBefore(cutoff);
        emailOutboxRepository.deleteFinishedBefore(Instant.now().minus(Duration.ofDays(30)));
        authRateLimiter.purgeOldCounters();
        tenantInvitationRepository.expirePending(Instant.now());
        notificationRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(90)));
    }

    // The trigger on audit_events only allows deletes after this setting is switched on
    // for the current transaction.
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeOldAuditEvents() {
        int days = Math.max(auditRetentionDays, MINIMUM_AUDIT_RETENTION_DAYS);
        jdbcTemplate.queryForObject("SELECT set_config('docket.audit_purge', 'on', true)", String.class);
        jdbcTemplate.update("DELETE FROM audit_events WHERE created_at < now() - make_interval(days => ?)", days);
    }
}
