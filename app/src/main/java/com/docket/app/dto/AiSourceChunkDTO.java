package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class AiSourceChunkDTO {

    private UUID documentId;
    private String documentName;
    private String chunkText;
    private Integer chunkIndex;
}
