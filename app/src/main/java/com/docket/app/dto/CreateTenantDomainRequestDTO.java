package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantDomainRequestDTO(@NotBlank(message = "Domain is required") String domain) {
}
