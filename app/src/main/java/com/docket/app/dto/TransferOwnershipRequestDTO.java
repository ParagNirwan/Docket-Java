package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferOwnershipRequestDTO {
    @NotNull(message = "New owner membership ID is required")
    private UUID newOwnerMembershipId;

    @NotBlank(message = "Password is required")
    private String password;
}
