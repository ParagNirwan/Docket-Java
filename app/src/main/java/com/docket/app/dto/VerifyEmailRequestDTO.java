package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequestDTO {
    @NotBlank
    private String token;
}
