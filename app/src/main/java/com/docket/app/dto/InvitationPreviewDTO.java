package com.docket.app.dto;

import com.docket.app.enums.Roles;

// What the holder of an invite link can see before accepting it.
public record InvitationPreviewDTO(
        String tenantName,
        String tenantSlug,
        String inviterName,
        String email,
        Roles role,
        boolean hasAccount
) {
}
