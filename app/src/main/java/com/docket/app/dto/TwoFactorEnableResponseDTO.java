package com.docket.app.dto;

import java.util.List;

public record TwoFactorEnableResponseDTO(
        List<String> backupCodes
) {
}

