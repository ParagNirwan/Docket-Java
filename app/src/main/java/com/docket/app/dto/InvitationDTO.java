package com.docket.app.dto;

import com.docket.app.enums.Roles;

import java.time.Instant;
import java.util.UUID;

// A pending invite, as shown to the people managing an organization.
public record InvitationDTO(
        UUID id,
        String email,
        Roles role,
        String invitedByName,
        Instant createdAt,
        Instant expiresAt
) {
}
