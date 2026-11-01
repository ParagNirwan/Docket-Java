package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSourceChunkDTO {

    private UUID documentId;
    private String documentName;
    private String chunkText;
    private Integer chunkIndex;
    private Double relevanceScore;
    private Integer pageStart;
    private Integer pageEnd;
    private String heading;
}
