package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.docket.app.enums.IndexingStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentResponseDTO {
    private UUID id;
    private String originalFileName;
    private String contentType;
    private UUID uploadedBy;
    private String uploadedByName;
    private Long fileSize;
    private Instant createdAt;
    private Instant updatedAt;
    private IndexingStatus indexingStatus;
    private UUID folderId;

}
