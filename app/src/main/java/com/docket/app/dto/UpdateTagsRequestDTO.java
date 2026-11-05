package com.docket.app.dto;

import java.util.List;
import java.util.UUID;

public record UpdateTagsRequestDTO(
        List<UUID> tagIds
) {
}

