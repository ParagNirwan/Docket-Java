package com.docket.app.dto;

public record CreatedServiceAccountDTO(
        ServiceAccountDTO serviceAccount,
        String clientSecret
) {
}