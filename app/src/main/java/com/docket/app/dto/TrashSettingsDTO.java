package com.docket.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TrashSettingsDTO(
        @Min(value = 1, message = "Must be at least 1 day")
        @Max(value = 3650, message = "Must be at most 3650 days")
        int trashRetentionDays
) {
}
