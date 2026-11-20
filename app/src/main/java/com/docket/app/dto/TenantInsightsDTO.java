package com.docket.app.dto;

import java.util.Map;

// Plan usage at a glance for the Insights tab. Limits are null when unlimited or when
// billing is switched off (self-hosted).
public record TenantInsightsDTO(
        String plan,
        boolean billingEnforced,
        long seatsUsed,
        Integer seatLimit,
        int aiQueriesThisMonth,
        Integer aiQueryLimit,
        long auditEventsLast30Days,
        Map<String, Long> topActionsLast30Days
) {
}
