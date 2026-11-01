package com.docket.app.dto;

import com.docket.app.enums.LockMode;

import java.time.Instant;
import java.util.UUID;

// retainUntil is null when no policy covers the destination.
public record ArchivePreviewDTO(UUID folderId, String folderName, Instant retainUntil, LockMode lockMode) {
}
