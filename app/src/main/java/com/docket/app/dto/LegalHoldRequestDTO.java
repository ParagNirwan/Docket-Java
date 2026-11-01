package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LegalHoldRequestDTO(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,
        @NotBlank(message = "Reason is required")
        @Size(max = 2000, message = "Reason must be at most 2000 characters")
        String reason,
        @NotNull(message = "Choose what the hold covers")
        LegalHoldTargetsDTO targets
) {
}
