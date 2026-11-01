package com.docket.app.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkArchiveRequestDTO(
        @NotEmpty(message = "Choose at least one document") List<UUID> documentIds,
        UUID folderId
) {
}
