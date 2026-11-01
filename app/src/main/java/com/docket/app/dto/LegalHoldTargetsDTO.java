package com.docket.app.dto;

import java.util.List;
import java.util.UUID;

// What a hold covers. Any mix: folders include everything beneath them, and a search
// covers the documents matching it right now.
public record LegalHoldTargetsDTO(List<UUID> documentIds, List<UUID> folderIds, String searchQuery) {
}
