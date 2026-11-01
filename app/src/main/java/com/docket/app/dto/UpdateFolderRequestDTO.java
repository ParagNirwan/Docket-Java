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
public class UpdateFolderRequestDTO {
    @Size(max = 255, message = "Folder name must be at most 255 characters")
    private String name;

    private UUID parentFolderId;
}
