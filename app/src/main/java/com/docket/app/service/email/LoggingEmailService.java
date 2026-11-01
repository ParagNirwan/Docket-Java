package com.docket.app.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

// For local development without a mail server: prints emails to the console.
// Bodies can hold reset links, so this doesn't exist in production. There, choosing
// this provider stops the app from starting instead of writing links to the logs.
@Slf4j
@Service
@Profile("!prod & !gcp")
@ConditionalOnProperty(name = "docket.email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void send(EmailMessage message) {
        log.info("Email to {} - {}\n{}", message.to(), message.subject(), message.textBody());
    }
}
