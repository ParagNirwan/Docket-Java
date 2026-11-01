package com.docket.app.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// For environments with no email provider yet. The app runs normally, but nothing is
// sent. Unlike LoggingEmailService this is safe in production: bodies can hold reset
// links, so they're never logged, and failing permanently erases them from the outbox.
@Slf4j
@Service
@ConditionalOnProperty(name = "docket.email.provider", havingValue = "disabled")
public class DisabledEmailService implements EmailService {

    public DisabledEmailService() {
        log.warn("Email is disabled: password reset and verification emails will not be sent");
    }

    @Override
    public void send(EmailMessage message) {
        throw new EmailSendException("Email is disabled", true, null);
    }
}
