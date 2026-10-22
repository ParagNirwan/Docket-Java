package com.docket.app.service.ai;

import java.util.UUID;

public record DocumentUploadedEvent(UUID documentId, UUID tenantId) {
}
