package com.docket.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkMoveRequestDTO(
        @NotEmpty(message = "Document list cannot be empty")
        List<UUID> documentIds,

        @NotNull(message = "Target folder is required")
        UUID targetFolderId
) {
}

