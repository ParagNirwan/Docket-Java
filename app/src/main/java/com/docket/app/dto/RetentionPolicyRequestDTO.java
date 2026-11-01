package com.docket.app.dto;

import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.OnExpiry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RetentionPolicyRequestDTO(
        @NotNull(message = "Choose a folder") UUID folderId,
        @Min(value = 1, message = "Must be at least 1 day")
        @Max(value = 36500, message = "Must be at most 100 years")
        int retentionDays,
        @NotNull(message = "Choose a lock mode") LockMode lockMode,
        @NotNull(message = "Choose when the clock starts") ClockStart clockStart,
        boolean appliesToSubfolders,
        @NotNull(message = "Choose what happens on expiry") OnExpiry onExpiry
) {
}
