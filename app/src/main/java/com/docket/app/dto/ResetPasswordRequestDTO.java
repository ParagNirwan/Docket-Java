package com.docket.app.dto;

import com.docket.app.security.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    @NotBlank
    private String token;

    @ValidPassword
    private String newPassword;
}
