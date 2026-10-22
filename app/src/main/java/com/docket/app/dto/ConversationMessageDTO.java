package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ConversationMessageDTO {

    private UUID id;
    private String role;
    private String content;
    private List<AiSourceChunkDTO> sources;
    private Instant createdAt;
}
