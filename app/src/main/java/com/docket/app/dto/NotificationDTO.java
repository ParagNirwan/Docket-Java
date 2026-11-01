package com.docket.app.dto;

import com.docket.app.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDTO(UUID id, NotificationType type, String title, String body, String link, boolean read, Instant createdAt) {
}
