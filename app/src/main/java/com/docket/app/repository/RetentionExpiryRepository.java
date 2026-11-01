package com.docket.app.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// The queries behind the retention expiry job. Each one only touches documents it hasn't
// handled yet, so running the job twice changes nothing.
@Repository
public class RetentionExpiryRepository {
    private static final String EXPIRING = """
            FROM documents d
            JOIN retention_policies p ON p.id = d.retention_policy_id
            WHERE d.status = 'ACTIVE'
                AND d.expiring_notified_at IS NULL
                AND d.retain_until > :now AND d.retain_until <= :horizon
                AND p.on_expiry <> 'NONE'
            """;

    private static final String EXPIRED_NOTIFY = """
            FROM documents d
            JOIN retention_policies p ON p.id = d.retention_policy_id
            WHERE d.status = 'ACTIVE'
                AND d.expiry_handled_at IS NULL
                AND d.retain_until <= :now
                AND p.on_expiry = 'NOTIFY'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetentionExpiryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TenantExpiry> findExpiring(Instant now, Instant horizon) {
        return summarize(EXPIRING, new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("horizon", Timestamp.from(horizon)));
    }

    public void markExpiringNotified(UUID tenantId, Instant now, Instant horizon) {
        jdbcTemplate.update(
                "UPDATE documents SET expiring_notified_at = :now WHERE id IN (SELECT d.id " + EXPIRING + " AND d.tenant_id = :tenantId)",
                new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("horizon", Timestamp.from(horizon))
                .addValue("tenantId", tenantId));
    }

    public List<TenantExpiry> findExpiredToNotify(Instant now) {
        return summarize(EXPIRED_NOTIFY, new MapSqlParameterSource().addValue("now", Timestamp.from(now)));
    }

    public void markExpiredNotified(UUID tenantId, Instant now) {
        jdbcTemplate.update(
                "UPDATE documents SET expiry_handled_at = :now WHERE id IN (SELECT d.id " + EXPIRED_NOTIFY + " AND d.tenant_id = :tenantId)",
                new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("tenantId", tenantId));
    }

    // Nothing to do for these: the policy says so, or it has since been removed.
    public int markNoActionNeeded(Instant now) {
        return jdbcTemplate.update("""
                UPDATE documents d SET expiry_handled_at = :now
                WHERE d.status = 'ACTIVE' AND d.expiry_handled_at IS NULL AND d.retain_until <= :now
                    AND NOT EXISTS (
                        SELECT 1 FROM retention_policies p
                        WHERE p.id = d.retention_policy_id AND p.on_expiry <> 'NONE'
                    )
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)));
    }

    // Held documents stay unhandled, so they're picked up once the hold is released.
    public List<UUID> findDueForAutoDelete(Instant now, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT d.id FROM documents d
                JOIN retention_policies p ON p.id = d.retention_policy_id
                WHERE d.status = 'ACTIVE' AND d.expiry_handled_at IS NULL AND d.retain_until <= :now
                    AND p.on_expiry = 'AUTO_DELETE' AND NOT d.legal_hold
                ORDER BY d.retain_until
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("limit", limit), UUID.class);
    }

    // Past each organization's own trash period, and not locked.
    public List<UUID> findDueForPurge(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT d.id FROM documents d
                JOIN tenants t ON t.id = d.tenant_id
                WHERE d.status = 'SOFT_DELETED'
                    AND d.deleted_at < now() - make_interval(days => t.trash_retention_days)
                    AND NOT d.legal_hold
                    AND (d.retain_until IS NULL OR d.retain_until <= now())
                ORDER BY d.deleted_at
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("limit", limit), UUID.class);
    }

    private List<TenantExpiry> summarize(String from, MapSqlParameterSource params) {
        return jdbcTemplate.query(
                "SELECT d.tenant_id, count(*) AS documents, min(d.retain_until) AS earliest " + from + " GROUP BY d.tenant_id",
                params,
                (rs, rowNum) -> new TenantExpiry(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getLong("documents"),
                        rs.getTimestamp("earliest").toInstant()));
    }

    public record TenantExpiry(UUID tenantId, long documents, Instant earliest) {
    }
}
