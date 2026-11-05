package com.docket.app.dto;

import java.util.List;
import java.util.UUID;

public record TrayPermissionsDTO(List<UUID> userIds) {
}

