package com.docket.app.dto;

import java.util.Map;

public record UpdateMetadataRequestDTO(
        Map<String, Object> metadata
) {
}

