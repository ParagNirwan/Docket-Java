package com.docket.app.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkTagRequestDTO(
        @NotEmpty(message = "Document list cannot be empty")
        List<UUID> documentIds,
        List<UUID> tagIdsToAdd,
        List<UUID> tagIdsToRemove
) {
}

