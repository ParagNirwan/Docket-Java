package com.docket.app.dto;

import com.docket.app.enums.ConversationVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    private UUID id;
    private String title;
    private ConversationVisibility visibility;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;
    private List<ConversationMessageDTO> messages;

    public ConversationDTO(UUID id, String title, Instant createdAt, Instant updatedAt, int messageCount,
                           List<ConversationMessageDTO> messages) {
        this(id, title, ConversationVisibility.PRIVATE, createdAt, updatedAt, messageCount, messages);
    }
}
