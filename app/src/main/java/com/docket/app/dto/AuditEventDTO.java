package com.docket.app.dto;

import com.docket.app.enums.AuditAction;
import com.docket.app.enums.AuditActorType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventDTO(
        UUID id,
        UUID actorUserId,
        String actorName,
        String actorEmail,
        AuditActorType actorType,
        AuditAction action,
        String targetType,
        UUID targetId,
        Map<String, Object> metadata,
        String ip,
        String userAgent,
        Instant createdAt
) {
}
