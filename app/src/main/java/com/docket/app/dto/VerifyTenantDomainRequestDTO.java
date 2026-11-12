package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyTenantDomainRequestDTO(
        @NotBlank(message = "Domain is required") String domain,
        @NotBlank(message = "Verification token is required") String token
) {
}
