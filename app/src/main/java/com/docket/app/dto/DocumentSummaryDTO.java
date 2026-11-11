package com.docket.app.dto;

import java.time.Instant;
import java.util.List;

// summary is null when the document has none: not indexed yet, nothing to read, or
// summaries are switched off for the organization.
public record DocumentSummaryDTO(String summary, List<String> keyPoints, Instant generatedAt) {
}
