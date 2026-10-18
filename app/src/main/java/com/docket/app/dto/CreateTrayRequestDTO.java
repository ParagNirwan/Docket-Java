package com.docket.app.dto;

import com.docket.app.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTrayRequestDTO {
    @NotBlank(message = "Tray name is required")
    private String name;
    private String description;
    @NotNull
    private Visibility visibility;
}
