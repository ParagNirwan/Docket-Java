package com.docket.app.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceAccountDTO(
        UUID id,
        String name,
        String clientId,
        List<String> scopes,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {
}