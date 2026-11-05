package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private boolean twoFactorRequired;
    private String twoFactorTempToken;

    public AuthResponseDTO(String token) {
        this.token = token;
        this.twoFactorRequired = false;
        this.twoFactorTempToken = null;
    }

    public static AuthResponseDTO twoFactorRequired(String tempToken) {
        return new AuthResponseDTO(null, true, tempToken);
    }
}
