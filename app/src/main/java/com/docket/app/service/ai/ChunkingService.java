package com.docket.app.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 2000; // ~500 tokens
    private static final int OVERLAP_SIZE = 200; // ~50 tokens
    private static final String SEGMENT_SEPARATOR = "\n\n";

    private record Span(TextSegment segment, int start, int end) {
    }

    public List<TextChunk> chunk(List<TextSegment> segments) {
        StringBuilder joined = new StringBuilder();
        List<Span> spans = new ArrayList<>();

        for (TextSegment segment : segments) {
            String text = segment.text() == null ? "" : segment.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(SEGMENT_SEPARATOR);
            }
            spans.add(new Span(segment, joined.length(), joined.length() + text.length()));
            joined.append(text);
        }

        String text = joined.toString();
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // If not at the end, try to find a natural break point
            if (end < text.length()) {
                end = findBreakPoint(text, start, end);
            }

            addChunk(chunks, text, start, end, spans);

            // The last chunk already reaches the end of the text
            if (end == text.length()) {
                break;
            }

            // Move forward by (chunk length - overlap)
            start = Math.max(end - OVERLAP_SIZE, start + 1);
        }

        return chunks;
    }

    private void addChunk(List<TextChunk> chunks, String text, int start, int end, List<Span> spans) {
        String raw = text.substring(start, end);
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            return;
        }

        int charStart = start + raw.indexOf(stripped);
        int charEnd = charStart + stripped.length();
        int middle = (charStart + charEnd) / 2;

        Integer pageStart = null;
        Integer pageEnd = null;
        String heading = null;

        for (Span span : spans) {
            if (span.start() >= charEnd) {
                break;
            }
            // The overlap can start a chunk in the section before, so the heading
            // comes from whichever section the middle of the chunk falls in.
            if (span.start() <= middle) {
                heading = span.segment().heading();
            }
            if (span.end() > charStart && span.segment().page() != null) {
                pageStart = pageStart == null ? span.segment().page() : pageStart;
                pageEnd = span.segment().page();
            }
        }

        chunks.add(new TextChunk(stripped, pageStart, pageEnd, heading, charStart, charEnd));
    }

    // A break must leave at least half a chunk, otherwise a short paragraph near the
    // start would produce a tiny chunk and barely move the window forward.
    private int findBreakPoint(String text, int start, int end) {
        int earliest = start + CHUNK_SIZE / 2;

        // First try: paragraph break
        int paraBreak = text.lastIndexOf("\n\n", end);
        if (paraBreak > earliest) {
            return paraBreak;
        }

        // Second try: sentence break
        int sentenceBreak = text.lastIndexOf(". ", end);
        if (sentenceBreak > earliest) {
            return sentenceBreak + 1; // include the period
        }

        // Fallback: hard cut at chunk size
        return end;
    }

}
