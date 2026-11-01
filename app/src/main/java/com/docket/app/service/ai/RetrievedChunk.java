package com.docket.app.service.ai;

import java.util.UUID;

// Distance is null when the chunk was fetched by position rather than by similarity.
// exactMatch means the chunk contains every word of the keyword search.
public record RetrievedChunk(
        UUID documentId,
        String documentName,
        Integer chunkIndex,
        String chunkText,
        Integer pageStart,
        Integer pageEnd,
        String heading,
        Double distance,
        boolean exactMatch) {
}
