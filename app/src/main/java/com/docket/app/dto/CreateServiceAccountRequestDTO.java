package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateServiceAccountRequestDTO(
        @NotBlank @Size(max = 100) String name,
        List<@NotBlank String> scopes
) {
}