package com.docket.app.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Leave a field out to keep its current value.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentRequestDTO {
    @Size(max = 512, message = "Name must be at most 512 characters")
    private String name;

    private UUID folderId;
}
