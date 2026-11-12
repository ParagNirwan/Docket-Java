package com.docket.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// The rows from a preview that the person chose to send. Each one is checked again.
public record BulkInviteRequestDTO(
        @NotEmpty(message = "There is nobody to invite")
        @Size(max = 200, message = "At most 200 people can be invited at once") List<Row> rows
) {
    public record Row(String email, String role, String name) {
    }
}
