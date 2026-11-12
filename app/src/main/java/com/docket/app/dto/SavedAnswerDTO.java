package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// A question and its verified AI answer pinned by a team member for the organization.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedAnswerDTO {

    private UUID conversationId;
    private String conversationTitle;
    private UUID messageId;
    private String question;
    private String answer;
    private List<AiSourceChunkDTO> sources;
    private Instant pinnedAt;
    private UUID pinnedBy;
}
