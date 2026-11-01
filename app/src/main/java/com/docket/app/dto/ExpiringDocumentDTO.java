package com.docket.app.dto;

import com.docket.app.enums.LockMode;

import java.time.Instant;
import java.util.UUID;

public record ExpiringDocumentDTO(UUID id, String originalFileName, UUID folderId, Instant retainUntil, LockMode lockMode) {
}
