package com.docket.app.dto;

import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Roles;

import java.time.Instant;
import java.util.UUID;

public record MemberDTO(
        UUID membershipId,
        String name,
        String email,
        Roles role,
        MemberStatus status,
        Instant joinedAt,
        Instant lastActiveAt,
        boolean currentUser
) {
}
