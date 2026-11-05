package com.docket.app.dto;

import java.util.UUID;

public record TagDTO(
        UUID id,
        String name,
        String color
) {
}

