package com.docket.app.controller;

import com.docket.app.service.DocumentLockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class RetentionExceptionHandler {
    // The frontend needs these details to explain the lock and to offer an override.
    @ExceptionHandler(DocumentLockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(DocumentLockedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", e.getReason());
        body.put("retainUntil", e.getRetainUntil());
        body.put("lockMode", e.getLockMode());
        body.put("legalHold", e.isLegalHold());
        body.put("archived", e.isArchived());
        return ResponseEntity.status(HttpStatus.LOCKED).body(body);
    }
}
