package com.docket.app.dto;

import java.time.Instant;

public record PublicShareInfoDTO(
        String documentName,
        Long fileSize,
        String contentType,
        boolean requiresPassword,
        boolean expired,
        boolean limitReached,
        Instant expiresAt
) {
}

