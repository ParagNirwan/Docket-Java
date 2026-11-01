package com.docket.app.dto;

import com.docket.app.enums.LockMode;

import java.time.Instant;
import java.util.UUID;

// originalFolderName is null when the folder is gone, and a restore goes to the Inbox.
public record TrashDocumentDTO(
        UUID id,
        String originalFileName,
        String contentType,
        Long fileSize,
        Instant deletedAt,
        String deletedByName,
        String originalFolderName,
        Instant purgeAfter,
        Instant retainUntil,
        LockMode lockMode,
        boolean legalHold
) {
}
