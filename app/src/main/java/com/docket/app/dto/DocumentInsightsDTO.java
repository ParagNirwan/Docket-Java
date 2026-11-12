package com.docket.app.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DocumentInsightsDTO(
        String documentType,
        List<String> suggestedTags,
        Map<String, Object> fields,
        LocalDate keyDate,
        Instant reviewedAt
) {
}
