package com.docket.app.dto;

import java.time.Instant;
import java.util.UUID;

// snippet is plain text with each match between the control characters U+0002 and U+0003,
// so the frontend can highlight matches without treating a document's text as HTML.
// It's null when only the file name matched.
public record SearchResultDTO(
        UUID documentId,
        String fileName,
        String contentType,
        Long fileSize,
        String uploadedByName,
        Instant createdAt,
        UUID folderId,
        String folderName,
        UUID trayId,
        String trayName,
        String snippet,
        Integer page
) {
}
