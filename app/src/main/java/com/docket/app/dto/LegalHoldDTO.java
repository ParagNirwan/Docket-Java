package com.docket.app.dto;

import java.time.Instant;
import java.util.UUID;

public record LegalHoldDTO(
        UUID id,
        String name,
        String reason,
        String createdByName,
        Instant createdAt,
        Instant releasedAt,
        long documentCount
) {
}
