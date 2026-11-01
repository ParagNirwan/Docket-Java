package com.docket.app.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionDTO(UUID id, String userAgent, String ip, Instant lastActiveAt, boolean current) {
}
