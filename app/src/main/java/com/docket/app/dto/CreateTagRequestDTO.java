package com.docket.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTagRequestDTO(
        @NotBlank(message = "Tag name is required")
        @Size(max = 64, message = "Tag name cannot exceed 64 characters")
        String name,

        @Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a valid hex code (e.g. #4F46E5)")
        String color
) {
}

