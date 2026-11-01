package com.docket.app.service.email;

// Actually hands one email to a provider. Features shouldn't call this directly:
// use EmailOutboxService, which survives restarts and retries failures.
public interface EmailService {
    void send(EmailMessage message);
}
