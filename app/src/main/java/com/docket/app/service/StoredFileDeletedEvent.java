package com.docket.app.service;

// A document's row is gone. Published inside the transaction, acted on after it commits.
public record StoredFileDeletedEvent(String storedFileName) {
}
