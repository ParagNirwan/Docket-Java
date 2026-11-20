package com.docket.app.service;

import com.docket.app.dto.TenantInsightsDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Plan;
import com.docket.app.repository.TenantRepository;
import com.docket.app.service.billing.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantInsightsService {
    private static final int TOP_ACTIONS = 5;

    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final PlanLimitService planLimitService;
    private final JdbcTemplate jdbcTemplate;

    public TenantInsightsDTO insights(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        Plan plan = planLimitService.planOf(tenant);
        boolean enforced = planLimitService.isEnforced();
        Timestamp since = Timestamp.from(Instant.now().minus(Duration.ofDays(30)));

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE tenant_id = ? AND created_at >= ?",
                Long.class, tenant.getId(), since);

        Map<String, Long> top = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT action, count(*) AS n FROM audit_events
                        WHERE tenant_id = ? AND created_at >= ?
                        GROUP BY action ORDER BY n DESC, action LIMIT ?
                        """,
                rs -> {
                    top.put(rs.getString("action"), rs.getLong("n"));
                },
                tenant.getId(), since, TOP_ACTIONS);

        return new TenantInsightsDTO(
                plan.name(),
                enforced,
                planLimitService.seatsUsed(tenant.getId()),
                enforced ? plan.getSeats() : null,
                planLimitService.aiQueriesUsed(tenant.getId()),
                enforced ? plan.getAiQueriesPerMonth() : null,
                total == null ? 0 : total,
                top
        );
    }
}
