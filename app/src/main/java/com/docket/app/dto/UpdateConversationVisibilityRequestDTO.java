package com.docket.app.dto;

import com.docket.app.enums.ConversationVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationVisibilityRequestDTO {

    @NotNull(message = "Visibility is required")
    private ConversationVisibility visibility;
}
