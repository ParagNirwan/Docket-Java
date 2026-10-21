package com.docket.app.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    void chunk_nullText_returnsEmptyList() {
        List<String> result = chunkingService.chunk(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void chunk_emptyText_returnsEmptyList() {
        List<String> result = chunkingService.chunk("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "This is a short document.";
        List<String> result = chunkingService.chunk(text);
        assertEquals(1, result.size());
        assertEquals(text, result.get(0));
    }

    @Test
    void chunk_longText_returnsMultipleChunks() {
        // Build a string longer than CHUNK_SIZE (2000)
        String text = "word ".repeat(600); // 3000 chars
        List<String> result = chunkingService.chunk(text);
        assertTrue(result.size() > 1);
    }

    @Test
    void chunk_chunksHaveOverlap() {
        String text = "word ".repeat(600);
        List<String> result = chunkingService.chunk(text);
        // The end of chunk 1 should appear at the start of chunk 2
        String endOfFirst = result.get(0).substring(result.get(0).length() - 50);
        assertTrue(result.get(1).contains(endOfFirst));
    }

    @Test
    void chunk_respectsParagraphBoundaries() {
        // Two paragraphs, each under chunk size but together over it
        String para1 = "a ".repeat(900);  // 1800 chars
        String para2 = "b ".repeat(900);
        String text = para1 + "\n\n" + para2;
        List<String> result = chunkingService.chunk(text);
        // Should split at the paragraph break, not mid-word
        assertTrue(result.get(0).endsWith("a"));
        assertFalse(result.get(0).contains("b"));
    }
}
