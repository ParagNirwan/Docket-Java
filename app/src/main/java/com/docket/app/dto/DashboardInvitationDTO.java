package com.docket.app.dto;

import com.docket.app.enums.Roles;

import java.time.Instant;
import java.util.UUID;

public record DashboardInvitationDTO(
        UUID id,
        String tenantName,
        String tenantSlug,
        Roles role,
        String invitedByName,
        Instant createdAt,
        Instant expiresAt
) {
}
