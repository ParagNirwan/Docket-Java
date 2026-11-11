package com.docket.app.dto;

import com.docket.app.service.ai.AiSearchScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.docket.app.enums.AiQueryMode;

@Data
public class CreateConversationRequestDTO {

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question must not exceed 1000 characters")
    private String question;

    private AiQueryMode mode = AiQueryMode.ASK;

    // Narrows a workspace search. Ignored when asking about a single document.
    @Valid
    private AiSearchScope scope;
}
