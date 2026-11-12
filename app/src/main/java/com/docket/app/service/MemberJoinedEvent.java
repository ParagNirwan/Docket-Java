package com.docket.app.service;

import java.util.UUID;

// Someone became an active member, by whichever route. Webhooks listen for this.
public record MemberJoinedEvent(UUID tenantId, UUID userId, String role) {
}
