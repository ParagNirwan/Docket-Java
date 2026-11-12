package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateWebhookRequestDTO(
        @NotBlank String url,
        @NotNull List<String> events
) {
}
