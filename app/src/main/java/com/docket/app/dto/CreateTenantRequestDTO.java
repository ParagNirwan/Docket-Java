package com.docket.app.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequestDTO {
    @NotBlank(message = "Tenant name is required")
    private String tenantName;

    @NotBlank(message = "Slug is required")
    @JsonAlias({"tenantSlug"})
    private String slug;
}
