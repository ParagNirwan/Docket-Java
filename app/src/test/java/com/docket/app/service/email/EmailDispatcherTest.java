package com.docket.app.service.email;

import com.docket.app.entity.EmailOutbox;
import com.docket.app.enums.EmailStatus;
import com.docket.app.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {
    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailDispatcher dispatcher;

    private EmailOutbox email(int attempts) {
        EmailOutbox email = new EmailOutbox();
        email.setToAddress("ada@example.com");
        email.setSubject("Subject");
        email.setHtmlBody("<a href='https://docket.test/reset?token=secret'>Reset</a>");
        email.setTextBody("https://docket.test/reset?token=secret");
        email.setStatus(EmailStatus.PENDING);
        email.setAttempts(attempts);
        return email;
    }

    @Test
    void sentEmailLosesItsBodySoResetLinksDontLingerInTheDatabase() {
        EmailOutbox email = email(1);

        dispatcher.deliver(email);

        assertEquals(EmailStatus.SENT, email.getStatus());
        assertNotNull(email.getFinishedAt());
        assertNull(email.getHtmlBody());
        assertNull(email.getTextBody());
        verify(emailOutboxRepository).save(email);
    }

    @Test
    void transientFailureKeepsTheEmailAndBacksOff() {
        EmailOutbox email = email(2);
        doThrow(new EmailSendException("SMTP down", false, null)).when(emailService).send(any());

        dispatcher.deliver(email);

        assertEquals(EmailStatus.PENDING, email.getStatus());
        assertEquals("SMTP down", email.getLastError());
        assertNotNull(email.getTextBody());
        assertTrue(email.getNextAttemptAt().isAfter(Instant.now().plus(Duration.ofMinutes(4))));
    }

    @Test
    void unexpectedExceptionsAreRetriedToo() {
        EmailOutbox email = email(1);
        doThrow(new IllegalStateException("boom")).when(emailService).send(any());

        dispatcher.deliver(email);

        assertEquals(EmailStatus.PENDING, email.getStatus());
    }

    @Test
    void permanentFailureIsNotRetried() {
        EmailOutbox email = email(1);
        doThrow(new EmailSendException("Bad address", true, null)).when(emailService).send(any());

        dispatcher.deliver(email);

        assertEquals(EmailStatus.FAILED, email.getStatus());
        assertNull(email.getTextBody());
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        EmailOutbox email = email(EmailDispatcher.MAX_ATTEMPTS);
        doThrow(new EmailSendException("SMTP down", false, null)).when(emailService).send(any());

        dispatcher.deliver(email);

        assertEquals(EmailStatus.FAILED, email.getStatus());
    }

    @Test
    void everyAttemptBeforeTheLastHasABackoff() {
        for (int attempts = 1; attempts < EmailDispatcher.MAX_ATTEMPTS; attempts++) {
            EmailOutbox email = email(attempts);
            doThrow(new EmailSendException("SMTP down", false, null)).when(emailService).send(any());

            dispatcher.deliver(email);

            assertEquals(EmailStatus.PENDING, email.getStatus());
        }
    }
}
