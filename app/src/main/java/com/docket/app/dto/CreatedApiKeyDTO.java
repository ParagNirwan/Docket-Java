package com.docket.app.dto;

public record CreatedApiKeyDTO(
        ApiKeyDTO key,
        String value
) {
}
