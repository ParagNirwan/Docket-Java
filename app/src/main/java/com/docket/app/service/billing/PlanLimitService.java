package com.docket.app.service.billing;

import com.docket.app.entity.Tenant;
import com.docket.app.enums.InvitationStatus;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Plan;
import com.docket.app.repository.TenantInvitationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

// Applies what an organization's plan allows: seats, storage, AI questions and Compliance
// locks. Every check passes where billing is switched off, so a self-hosted installation
// is never limited. Going over a limit is a 402, which the frontend turns into an upgrade prompt.
@Service
@RequiredArgsConstructor
public class PlanLimitService {
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantInvitationRepository invitationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${docket.billing.enabled:false}")
    private boolean enabled;

    public boolean isEnforced() {
        return enabled;
    }

    public Plan planOf(Tenant tenant) {
        return tenant.getPlan() == null ? Plan.FREE : tenant.getPlan();
    }

    public long seatsUsed(UUID tenantId) {
        return membershipRepository.countByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE);
    }

    // For someone about to become a member.
    public void requireSeat(Tenant tenant) {
        requireSeats(tenant, seatsUsed(tenant.getId()) + 1);
    }

    // For new invites. Pending invites count too, or an organization could send a hundred
    // and only find out they don't fit when people try to accept.
    public void requireSeatsForInvites(Tenant tenant, int newInvites) {
        long pending = invitationRepository.countByTenantIdAndStatusAndExpiresAtAfter(
                tenant.getId(), InvitationStatus.PENDING, Instant.now());
        requireSeats(tenant, seatsUsed(tenant.getId()) + pending + newInvites);
    }

    private void requireSeats(Tenant tenant, long needed) {
        Integer seats = planOf(tenant).getSeats();
        if (enabled && seats != null && needed > seats) {
            throw limit("Your plan includes " + seats + " seats. Upgrade to add more people.");
        }
    }

    // Null when the plan sets no limit of its own.
    public Long storageLimitBytes(Tenant tenant) {
        return enabled ? planOf(tenant).getStorageBytes() : null;
    }

    public void requireLockMode(Tenant tenant, LockMode lockMode) {
        if (enabled && lockMode == LockMode.COMPLIANCE && !planOf(tenant).isComplianceLock()) {
            throw limit("Compliance locks are part of the Business plan.");
        }
    }

    // Counts one AI question against this month's allowance. The count and the check are
    // one statement, so simultaneous questions can't both take the last one.
    public void countAiQuery(UUID tenantId) {
        if (!enabled) {
            return;
        }
        int allowance = tenantRepository.findById(tenantId).map(this::planOf).orElse(Plan.FREE).getAiQueriesPerMonth();
        Integer used = jdbcTemplate.queryForObject("""
                INSERT INTO tenant_ai_usage (tenant_id, period, queries) VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, period) DO UPDATE SET queries = tenant_ai_usage.queries + 1
                RETURNING queries
                """, Integer.class, tenantId, currentPeriod());
        if (used != null && used > allowance) {
            throw limit("Your organization has used this month's " + allowance + " AI questions. Upgrade for more.");
        }
    }

    public int aiQueriesUsed(UUID tenantId) {
        return jdbcTemplate.query(
                "SELECT queries FROM tenant_ai_usage WHERE tenant_id = ? AND period = ?",
                (rs, rowNum) -> rs.getInt(1), tenantId, currentPeriod()).stream().findFirst().orElse(0);
    }

    private static LocalDate currentPeriod() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    }

    private static ResponseStatusException limit(String message) {
        return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, message);
    }
}
