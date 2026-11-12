package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageDTO {

    private UUID id;
    private String role;
    private String content;
    private List<AiSourceChunkDTO> sources;
    private Instant createdAt;
    private Instant pinnedAt;
    private UUID pinnedBy;

    public ConversationMessageDTO(UUID id, String role, String content, List<AiSourceChunkDTO> sources, Instant createdAt) {
        this(id, role, content, sources, createdAt, null, null);
    }
}
