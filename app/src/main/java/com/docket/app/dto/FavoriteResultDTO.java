package com.docket.app.dto;

import java.util.UUID;

public record FavoriteResultDTO(UUID documentId, boolean favorite) {
}
