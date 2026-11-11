package com.docket.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkTagRequestDTO(
        @NotEmpty(message = "Document list cannot be empty")
        @Size(max = 100, message = "Select no more than 100 documents")
        List<UUID> documentIds,
        @Size(max = 100, message = "Select no more than 100 tags to add")
        List<UUID> tagIdsToAdd,
        @Size(max = 100, message = "Select no more than 100 tags to remove")
        List<UUID> tagIdsToRemove
) {
}
