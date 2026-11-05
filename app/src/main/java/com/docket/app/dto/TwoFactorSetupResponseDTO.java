package com.docket.app.dto;

public record TwoFactorSetupResponseDTO(
        String secret,
        String qrUri
) {
}

