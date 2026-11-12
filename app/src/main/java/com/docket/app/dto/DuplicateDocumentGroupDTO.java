package com.docket.app.dto;

import java.util.List;

public record DuplicateDocumentGroupDTO(String checksumSha256, List<DocumentResponseDTO> documents) {
}
