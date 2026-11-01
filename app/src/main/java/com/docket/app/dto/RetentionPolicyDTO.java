package com.docket.app.dto;

import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.OnExpiry;

import java.time.Instant;
import java.util.UUID;

public record RetentionPolicyDTO(
        UUID id,
        UUID folderId,
        String folderName,
        int retentionDays,
        LockMode lockMode,
        ClockStart clockStart,
        boolean appliesToSubfolders,
        OnExpiry onExpiry,
        long lockedDocuments,
        Instant createdAt,
        Instant updatedAt
) {
}
