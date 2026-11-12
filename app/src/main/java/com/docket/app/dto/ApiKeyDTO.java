package com.docket.app.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyDTO(
        UUID id,
        String name,
        String prefix,
        List<String> scopes,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {
}
