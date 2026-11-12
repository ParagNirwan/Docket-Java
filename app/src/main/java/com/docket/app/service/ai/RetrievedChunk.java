package com.docket.app.service.ai;

import java.util.UUID;

// Distance is null when the chunk was fetched by position rather than by similarity.
// exactMatch means the chunk contains every word of the keyword search.
// rerankScore runs from 0 to 1 and is null unless the re-ranker scored this chunk.
public record RetrievedChunk(
        UUID documentId,
        String documentName,
        Integer chunkIndex,
        String chunkText,
        Integer pageStart,
        Integer pageEnd,
        String heading,
        Double distance,
        boolean exactMatch,
        Double rerankScore) {

    // A document's AI summary stands in for a chunk under this index and heading
    public static final int SUMMARY_INDEX = -1;
    public static final String SUMMARY_HEADING = "AI summary";

    public RetrievedChunk(UUID documentId, String documentName, Integer chunkIndex, String chunkText,
                          Integer pageStart, Integer pageEnd, String heading, Double distance, boolean exactMatch) {
        this(documentId, documentName, chunkIndex, chunkText, pageStart, pageEnd, heading, distance, exactMatch, null);
    }

    public RetrievedChunk withRerankScore(Double score) {
        return new RetrievedChunk(documentId, documentName, chunkIndex, chunkText, pageStart, pageEnd, heading,
                distance, exactMatch, score);
    }
}
