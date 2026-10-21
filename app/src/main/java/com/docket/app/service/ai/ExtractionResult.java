package com.docket.app.service.ai;

import com.docket.app.enums.IndexingStatus;

public record ExtractionResult(String text, IndexingStatus status) {

    public static ExtractionResult success(String text) {
        return new ExtractionResult(text, IndexingStatus.COMPLETED);
    }

    public static ExtractionResult notProcessable() {
        return new ExtractionResult(null, IndexingStatus.NOT_PROCESSABLE);
    }

    public static ExtractionResult failed() {
        return new ExtractionResult(null, IndexingStatus.FAILED);
    }
}
