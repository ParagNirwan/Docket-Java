package com.docket.app.dto;

import java.util.List;

// expiringSoon is the first few of expiringSoonCount.
public record RetentionSummaryDTO(
        long lockedDocuments,
        long expiringSoonCount,
        List<ExpiringDocumentDTO> expiringSoon,
        int trashRetentionDays
) {
}
