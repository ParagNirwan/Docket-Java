package com.docket.app.dto;

import com.docket.app.enums.DomainJoinMode;

import java.time.Instant;

public record TenantDomainDTO(
        String domain,
        String verificationToken,
        Instant verifiedAt,
        DomainJoinMode joinMode,
        boolean ssoRequired,
        Instant createdAt
) {
}
