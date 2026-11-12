package com.docket.app.dto;

import java.util.Map;
import java.util.UUID;

public record DocumentComparisonDTO(
        UUID leftDocumentId,
        UUID rightDocumentId,
        Map<String, Object> sharedFields,
        Map<String, Object> differences
) {
}
