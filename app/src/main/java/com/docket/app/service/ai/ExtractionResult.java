package com.docket.app.service.ai;

import com.docket.app.enums.IndexingStatus;

import java.util.List;
import java.util.stream.Collectors;

public record ExtractionResult(List<TextSegment> segments, IndexingStatus status) {

    public static ExtractionResult success(List<TextSegment> segments) {
        return new ExtractionResult(segments, IndexingStatus.COMPLETED);
    }

    public static ExtractionResult notProcessable() {
        return new ExtractionResult(List.of(), IndexingStatus.NOT_PROCESSABLE);
    }

    public static ExtractionResult failed() {
        return new ExtractionResult(List.of(), IndexingStatus.FAILED);
    }

    public String text() {
        if (status != IndexingStatus.COMPLETED) {
            return null;
        }
        return segments.stream().map(TextSegment::text).collect(Collectors.joining("\n\n"));
    }
}
