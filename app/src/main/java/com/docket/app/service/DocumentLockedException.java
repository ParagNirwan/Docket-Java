package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.enums.LockMode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

// A 423 that says what's protecting the document. See RetentionExceptionHandler.
@Getter
public class DocumentLockedException extends ResponseStatusException {
    private final Instant retainUntil;
    private final LockMode lockMode;
    private final boolean legalHold;
    private final boolean archived;

    public DocumentLockedException(String reason, Instant retainUntil, LockMode lockMode, boolean legalHold, boolean archived) {
        super(HttpStatus.LOCKED, reason);
        this.retainUntil = retainUntil;
        this.lockMode = lockMode;
        this.legalHold = legalHold;
        this.archived = archived;
    }

    static DocumentLockedException of(Document document, String reason) {
        return new DocumentLockedException(reason, document.getRetainUntil(), document.getLockMode(),
                document.isLegalHold(), document.getArchivedAt() != null);
    }
}
