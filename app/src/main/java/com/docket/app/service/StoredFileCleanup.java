package com.docket.app.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Files can't be un-deleted, so the database goes first: if the delete rolls back, or
// the retention trigger refuses it, the file is still there. If this step fails the
// worst outcome is an orphaned file, never a document pointing at nothing.
@Component
@RequiredArgsConstructor
public class StoredFileCleanup {
    private static final Logger log = LoggerFactory.getLogger(StoredFileCleanup.class);

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeleted(StoredFileDeletedEvent event) {
        try {
            storageService.delete(event.storedFileName());
        } catch (RuntimeException e) {
            log.error("Could not delete stored file {}. It is now orphaned", event.storedFileName(), e);
        }
    }
}
