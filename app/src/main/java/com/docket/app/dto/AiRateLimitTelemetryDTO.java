package com.docket.app.dto;

import java.util.List;

// Telemetry for AI rate limits, exposed to tenant admins to monitor capacity.
public record AiRateLimitTelemetryDTO(
        // Per-user limit configuration
        int userLimitPerWindow,
        long userWindowMillis,
        // Per-tenant limit configuration
        int tenantLimitPerWindow,
        long tenantWindowMillis,
        // Current usage for this tenant
        int currentTenantUsage,
        // Top users by usage in current window
        List<TopUserUsageDTO> topUsers,
        // Number of rate limit rejections in last hour
        long rejectionsLastHour
) {
    public record TopUserUsageDTO(
            String userId,
            String userEmail,
            int currentCount,
            int limit,
            double utilizationPercent
    ) {}
}