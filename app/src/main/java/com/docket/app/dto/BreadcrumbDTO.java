package com.docket.app.dto;

import java.util.UUID;

public record BreadcrumbDTO(UUID id, String name, boolean isTray) {
}
