package com.docket.app.dto;

import java.time.Instant;

public record CreateShareLinkRequestDTO(
        Instant expiresAt,
        String password,
        Integer maxDownloads
) {
}

