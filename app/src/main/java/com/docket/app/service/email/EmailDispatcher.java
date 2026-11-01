package com.docket.app.service.email;

import com.docket.app.entity.EmailOutbox;
import com.docket.app.enums.EmailStatus;
import com.docket.app.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Sends what's in the outbox. A new email is picked up straight after its transaction
// commits, and the regular poll catches retries and anything left over from a restart.
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {
    static final int MAX_ATTEMPTS = 6;
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6));

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onEmailQueued(EmailQueuedEvent event) {
        dispatchDue();
    }

    @Scheduled(fixedDelay = 10_000)
    public void dispatchDue() {
        List<EmailOutbox> batch;
        do {
            batch = emailOutboxRepository.claimDue(Instant.now().plus(LEASE), BATCH_SIZE);
            batch.forEach(this::deliver);
        } while (batch.size() == BATCH_SIZE);
    }

    // No transaction is held while talking to the provider: that could take seconds,
    // and would tie up a database connection the whole time.
    void deliver(EmailOutbox email) {
        try {
            emailService.send(new EmailMessage(
                    email.getToAddress(), email.getSubject(), email.getHtmlBody(), email.getTextBody()));
            finish(email, EmailStatus.SENT, null);
        } catch (RuntimeException e) {
            boolean permanent = e instanceof EmailSendException sendException && sendException.isPermanent();
            log.warn("Email {} failed on attempt {}: {}", email.getId(), email.getAttempts(), e.getMessage());
            if (permanent || email.getAttempts() >= MAX_ATTEMPTS) {
                finish(email, EmailStatus.FAILED, e.getMessage());
            } else {
                email.setLastError(truncate(e.getMessage()));
                email.setNextAttemptAt(Instant.now().plus(BACKOFF.get(email.getAttempts() - 1)));
                emailOutboxRepository.save(email);
            }
        }
    }

    private void finish(EmailOutbox email, EmailStatus status, String error) {
        email.setStatus(status);
        email.setLastError(truncate(error));
        email.setFinishedAt(Instant.now());
        email.setHtmlBody(null);
        email.setTextBody(null);
        emailOutboxRepository.save(email);
    }

    private String truncate(String value) {
        return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
