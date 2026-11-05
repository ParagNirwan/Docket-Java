package com.docket.app.dto;

import java.time.Instant;
import java.util.UUID;

public record ShareLinkDTO(
        UUID id,
        UUID documentId,
        String token,
        String shareUrl,
        Instant expiresAt,
        Integer maxDownloads,
        int downloadCount,
        boolean passwordProtected,
        boolean revoked,
        Instant createdAt
) {
}

