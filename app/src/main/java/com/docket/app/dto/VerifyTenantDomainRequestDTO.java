package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;

// Ownership is proven by the DNS TXT record, so the request only names the domain.
public record VerifyTenantDomainRequestDTO(
        @NotBlank(message = "Domain is required") String domain
) {
}
