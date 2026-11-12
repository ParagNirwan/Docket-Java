package com.docket.app.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointDTO(
        UUID id,
        String url,
        List<String> events,
        boolean active,
        Instant createdAt
) {
}
