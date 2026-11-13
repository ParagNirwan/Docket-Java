package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_/.][\\p{L}\\p{N}]+)*");
    private static final String SEGMENT_SEPARATOR = "\n\n";

    private final DocketAiProperties aiProperties;

    public ChunkingService() {
        this(new DocketAiProperties());
    }

    private record Span(TextSegment segment, int start, int end) {
    }

    private record Section(int start, int end) {
    }

    public List<TextChunk> chunk(List<TextSegment> segments) {
        StringBuilder joined = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        int sectionStart = 0;
        String previousHeading = null;
        boolean hasPreviousSegment = false;

        for (TextSegment segment : segments) {
            String text = segment.text() == null ? "" : segment.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (!joined.isEmpty()) {
                boolean headingChanged = hasPreviousSegment
                        && !Objects.equals(previousHeading, segment.heading());
                if (headingChanged) {
                    sections.add(new Section(sectionStart, joined.length()));
                }
                joined.append(SEGMENT_SEPARATOR);
                if (headingChanged) {
                    sectionStart = joined.length();
                }
            }
            spans.add(new Span(segment, joined.length(), joined.length() + text.length()));
            joined.append(text);
            previousHeading = segment.heading();
            hasPreviousSegment = true;
        }

        String text = joined.toString();
        List<TextChunk> chunks = new ArrayList<>();
        if (sectionStart < text.length()) {
            sections.add(new Section(sectionStart, text.length()));
        }
        for (Section section : sections) {
            int start = section.start();
            while (start < section.end()) {
                int end = findTokenEnd(text, start, section.end());
                if (end < section.end()) {
                    end = findBreakPoint(text, start, end);
                }
                end = keepTableRowsTogether(text, start, end, section.end());
                addChunk(chunks, text, start, end, spans);

                if (end == section.end()) {
                    break;
                }
                start = findOverlapStart(text, start, end);
            }
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

        String header = repeatedTableHeader(text, start);
        chunks.add(new TextChunk(header + stripped, pageStart, pageEnd, heading, charStart, charEnd));
    }

    private int findBreakPoint(String text, int start, int end) {
        int earliest = start + (end - start) / 2;

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

    private int findTokenEnd(String text, int start, int sectionEnd) {
        Matcher matcher = TOKEN.matcher(text);
        matcher.region(start, sectionEnd);
        int count = 0;
        while (matcher.find()) {
            if (++count == aiProperties.getChunking().getTokensPerChunk()) {
                return matcher.end();
            }
        }
        return sectionEnd;
    }

    private int findOverlapStart(String text, int start, int end) {
        Matcher matcher = TOKEN.matcher(text);
        matcher.region(start, end);
        List<Integer> tokenStarts = new ArrayList<>();
        while (matcher.find()) {
            tokenStarts.add(matcher.start());
        }
        if (tokenStarts.size() <= aiProperties.getChunking().getOverlapTokens()) {
            return end;
        }
        int nextStart = tokenStarts.get(tokenStarts.size() - aiProperties.getChunking().getOverlapTokens());
        int lineStart = text.lastIndexOf('\n', nextStart);
        int lineEnd = text.indexOf('\n', nextStart);
        if (lineStart >= 0 && lineEnd > nextStart
                && text.substring(lineStart + 1, lineEnd).contains("\t")) {
            return lineEnd + 1;
        }
        return Math.max(nextStart, start + 1);
    }

    private int keepTableRowsTogether(String text, int start, int end, int sectionEnd) {
        int lineStart = text.lastIndexOf('\n', Math.max(start, end - 1)) + 1;
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd < 0 || lineEnd >= sectionEnd) {
            lineEnd = sectionEnd;
        }
        if (!text.substring(lineStart, lineEnd).contains("\t")) {
            return end;
        }

        int rowStart = lineStart;
        int tableStart = rowStart;
        while (tableStart > start) {
            int priorEnd = tableStart - 1;
            int priorStart = text.lastIndexOf('\n', Math.max(start, priorEnd - 1)) + 1;
            if (!text.substring(priorStart, priorEnd).contains("\t")) {
                break;
            }
            tableStart = priorStart;
        }
        int tableEnd = lineEnd;
        while (tableEnd < sectionEnd) {
            int nextStart = tableEnd + 1;
            int nextEnd = text.indexOf('\n', nextStart);
            if (nextEnd < 0 || nextEnd > sectionEnd) {
                nextEnd = sectionEnd;
            }
            if (!text.substring(nextStart, nextEnd).contains("\t")) {
                break;
            }
            tableEnd = nextEnd;
        }

        if (start <= tableStart && countTokens(text, tableStart, tableEnd) <= aiProperties.getChunking().getMaxAtomicTableTokens()) {
            return tableEnd;
        }
        if (lineStart > start) {
            return lineStart;
        }
        return lineEnd;
    }

    private int countTokens(String text, int start, int end) {
        Matcher matcher = TOKEN.matcher(text);
        matcher.region(start, end);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String repeatedTableHeader(String text, int start) {
        int currentLineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int currentLineEnd = text.indexOf('\n', currentLineStart);
        if (currentLineEnd < 0 || !text.substring(currentLineStart, currentLineEnd).contains("\t")) {
            return "";
        }

        int tableStart = currentLineStart;
        while (tableStart > 0) {
            int previousEnd = tableStart - 1;
            int previousStart = text.lastIndexOf('\n', Math.max(0, previousEnd - 1)) + 1;
            if (!text.substring(previousStart, previousEnd).contains("\t")) {
                break;
            }
            tableStart = previousStart;
        }
        if (tableStart == currentLineStart) {
            return "";
        }
        int headerEnd = text.indexOf('\n', tableStart);
        return text.substring(tableStart, headerEnd < 0 ? text.length() : headerEnd) + "\n";
    }

}
