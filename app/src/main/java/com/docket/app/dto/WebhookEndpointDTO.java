package com.docket.app.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// signingSecret is only filled in when the endpoint is created; it's needed to check the
// X-Docket-Signature header and is never shown again.
public record WebhookEndpointDTO(
        UUID id,
        String url,
        List<String> events,
        boolean active,
        Instant createdAt,
        String signingSecret
) {
}
