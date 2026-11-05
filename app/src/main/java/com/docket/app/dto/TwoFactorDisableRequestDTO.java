package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequestDTO(
        @NotBlank(message = "Password is required")
        String password
) {
}

