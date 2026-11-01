package com.docket.app.dto;

import java.util.UUID;

// Leave folderId out to use the archive tray itself.
public record ArchiveRequestDTO(UUID folderId) {
}
