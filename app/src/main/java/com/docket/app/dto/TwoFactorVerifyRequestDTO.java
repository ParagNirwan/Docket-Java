package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequestDTO(
        @NotBlank(message = "Temporary token is required")
        String tempToken,

        @NotBlank(message = "Verification code is required")
        String code
) {
}

