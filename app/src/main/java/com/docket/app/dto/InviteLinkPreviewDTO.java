package com.docket.app.dto;

import com.docket.app.enums.Roles;

// What someone opening an invite link sees before they join.
public record InviteLinkPreviewDTO(String tenantName, String tenantSlug, Roles role) {
}
