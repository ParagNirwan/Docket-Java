package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ConversationDTO {

    private UUID id;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;
    private List<ConversationMessageDTO> messages;
}
