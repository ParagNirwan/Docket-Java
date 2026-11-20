package com.docket.app.service;

import com.docket.app.enums.AuditAction;

import java.time.Instant;
import java.util.UUID;

// Published after every audit record, inside the same transaction, so listeners such as
// webhooks can react to an action exactly when (and only if) it commits.
public record AuditRecordedEvent(
        UUID tenantId,
        UUID actorUserId,
        AuditAction action,
        String targetType,
        UUID targetId,
        Instant occurredAt
) {
}
