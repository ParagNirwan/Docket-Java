package com.docket.app.dto;

import java.util.List;

public record BulkOperationResultDTO(
        int succeeded,
        int failed,
        List<String> errors
) {
}

