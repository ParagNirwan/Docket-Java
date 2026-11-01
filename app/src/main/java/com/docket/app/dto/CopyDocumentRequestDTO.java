package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Leave folderId out to copy into the same folder.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyDocumentRequestDTO {
    private UUID folderId;
}
