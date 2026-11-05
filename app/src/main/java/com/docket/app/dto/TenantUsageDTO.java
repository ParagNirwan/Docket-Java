package com.docket.app.dto;

public record TenantUsageDTO(
        long storageBytesUsed,
        Long storageQuotaBytes,
        long documentCount
) {
}

