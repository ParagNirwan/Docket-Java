package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorEnableRequestDTO(
        @NotBlank(message = "Verification code is required")
        String code
) {
}

