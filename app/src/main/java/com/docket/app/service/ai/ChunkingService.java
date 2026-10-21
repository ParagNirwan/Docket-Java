package com.docket.app.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 2000; // ~500 tokens
    private static final int OVERLAP_SIZE = 200; // ~50 tokens

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String trimmed = text.trim();
        if (trimmed.length() <= CHUNK_SIZE) {
            return List.of(trimmed);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < trimmed.length()) {
            int end = Math.min(start + CHUNK_SIZE, trimmed.length());

            // If not at the end, try to find a natural break point
            if (end < trimmed.length()) {
                end = findBreakPoint(trimmed, start, end);
            }

            chunks.add(trimmed.substring(start, end).trim());

            // Move forward by (chunk length - overlap)
            start = Math.max(end - OVERLAP_SIZE, start + 1);

        }

        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        // First try: paragraph break
        int paraBreak = text.lastIndexOf("\n\n", end);
        if (paraBreak > start) {
            return paraBreak;
        }

        // Second try: sentence break
        int sentenceBreak = text.lastIndexOf(". ", end);
        if (sentenceBreak > start) {
            return sentenceBreak + 1; // include the period
        }

        // Fallback: hard cut at chunk size
        return end;
    }

}
