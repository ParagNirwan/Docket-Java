package com.docket.app.service.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisabledEmailServiceTest {

    @Test
    void failsPermanentlySoTheOutboxErasesTheBodyInsteadOfRetrying() {
        EmailMessage message = new EmailMessage("ada@example.com", "Subject", "<p>Hi</p>", "Hi");

        EmailSendException exception = assertThrows(
                EmailSendException.class,
                () -> new DisabledEmailService().send(message));

        assertTrue(exception.isPermanent());
    }
}
