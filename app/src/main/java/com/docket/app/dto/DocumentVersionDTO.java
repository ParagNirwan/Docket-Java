package com.docket.app.dto;
import java.time.Instant; import java.util.UUID;
public record DocumentVersionDTO(UUID id, int version, long fileSize, String contentType, UUID uploadedBy, Instant createdAt, String comment) {}
