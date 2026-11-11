package com.docket.app.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    private List<String> chunkText(String text) {
        return chunkingService.chunk(List.of(new TextSegment(text, null, null))).stream()
                .map(TextChunk::text)
                .toList();
    }

    @Test
    void chunk_nullText_returnsEmptyList() {
        List<String> result = chunkText(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void chunk_emptyText_returnsEmptyList() {
        List<String> result = chunkText("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "This is a short document.";
        List<String> result = chunkText(text);
        assertEquals(1, result.size());
        assertEquals(text, result.get(0));
    }

    @Test
    void chunk_longText_returnsMultipleChunks() {
        // Build a string longer than CHUNK_SIZE (2000)
        String text = "word ".repeat(600); // 3000 chars
        List<String> result = chunkText(text);
        assertTrue(result.size() > 1);
    }

    @Test
    void chunk_chunksHaveOverlap() {
        String text = "word ".repeat(600);
        List<String> result = chunkText(text);
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
        List<String> result = chunkText(text);
        // Should split at the paragraph break, not mid-word
        assertTrue(result.get(0).endsWith("a"));
        assertFalse(result.get(0).contains("b"));
    }

    @Test
    void chunk_longText_doesNotRepeatTheEndOfTheText() {
        String text = "word ".repeat(600);

        // 3000 characters need exactly two windows: 0 to 2000, then 1800 to the end.
        assertEquals(2, chunkText(text).size());
    }

    @Test
    void chunk_shortParagraphNearTheStart_doesNotProduceTinyChunks() {
        String text = "Title\n\n" + "word ".repeat(600);

        List<String> result = chunkText(text);

        assertEquals(2, result.size());
        assertTrue(result.get(0).startsWith("Title"));
        assertTrue(result.get(0).length() > 1000);
    }

    @Test
    void chunk_keepsPagesAcrossSegments() {
        List<TextChunk> result = chunkingService.chunk(List.of(
                new TextSegment("a ".repeat(200).strip(), 3, null),
                new TextSegment("b ".repeat(800).strip(), 4, null),
                new TextSegment("c ".repeat(800).strip(), 5, null)));

        assertEquals(3, result.get(0).pageStart());
        assertEquals(4, result.get(0).pageEnd());
        assertEquals(5, result.get(result.size() - 1).pageEnd());
    }

    @Test
    void chunk_doesNotMergeDifferentHeadingSections() {
        List<TextChunk> result = chunkingService.chunk(List.of(
                new TextSegment("a ".repeat(950).strip(), null, "Scope"),
                new TextSegment("b ".repeat(800).strip(), null, "Payment Terms")));

        assertEquals("Scope", result.get(0).heading());
        assertTrue(result.stream().anyMatch(chunk -> "Payment Terms".equals(chunk.heading())));
        assertTrue(result.stream().allMatch(chunk ->
                !(chunk.text().contains("a ") && chunk.text().contains("b "))));
        assertNull(result.get(0).pageStart());
    }

    @Test
    void chunk_offsetsPointIntoTheJoinedText() {
        String first = "a ".repeat(950).strip();
        String second = "b ".repeat(800).strip();
        String joined = first + "\n\n" + second;

        List<TextChunk> result = chunkingService.chunk(List.of(
                new TextSegment(first, null, null), new TextSegment(second, null, null)));

        for (TextChunk chunk : result) {
            assertEquals(chunk.text(), joined.substring(chunk.charStart(), chunk.charEnd()));
        }
    }

    @Test
    void chunk_keepsSmallTablesAtomicWhenTheyCrossTheTokenLimit() {
        String table = "A\tB\n" + "row value\tcell value\n".repeat(25);
        String text = "context ".repeat(450) + table;

        List<TextChunk> chunks = chunkingService.chunk(List.of(new TextSegment(text, null, null)));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("row value\tcell value"));
        assertEquals(25, chunks.get(0).text().lines().filter(line -> line.contains("\t")).count() - 1);
    }

    @Test
    void chunk_splitsLargeTablesBetweenRowsAndRepeatsTheHeader() {
        String header = "Name\tValue";
        String table = header + "\n" + java.util.stream.IntStream.range(0, 600)
                .mapToObj(index -> "row" + index + "\tvalue" + index)
                .collect(java.util.stream.Collectors.joining("\n"));

        List<TextChunk> chunks = chunkingService.chunk(List.of(new TextSegment(table, null, null)));

        assertTrue(chunks.size() > 1);
        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i).text().startsWith(header + "\n"));
        }
        for (TextChunk chunk : chunks) {
            assertTrue(chunk.text().lines().allMatch(line ->
                    line.equals(header) || line.matches("row\\d+\\tvalue\\d+")));
        }
    }
}
