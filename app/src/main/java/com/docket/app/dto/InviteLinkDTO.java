package com.docket.app.dto;

import com.docket.app.enums.Roles;

import java.time.Instant;
import java.util.UUID;

// url is only filled in when the link is created. Afterwards only its hash exists.
public record InviteLinkDTO(
        UUID id,
        Roles role,
        Integer maxUses,
        int uses,
        Instant expiresAt,
        String createdByName,
        Instant createdAt,
        String url
) {
}
