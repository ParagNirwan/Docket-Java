package com.docket.app.service.email;

import com.docket.app.entity.EmailOutbox;
import com.docket.app.enums.EmailStatus;
import com.docket.app.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

// The way features send email. The email is saved alongside the caller's own changes,
// so it goes out only if they commit, and EmailDispatcher takes it from there.
@Service
@RequiredArgsConstructor
public class EmailOutboxService {
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailTemplateRenderer renderer;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void enqueue(String to, EmailTemplate template, Map<String, Object> variables) {
        enqueue(to, template, template.getDefaultSubject(), variables);
    }

    @Transactional
    public void enqueue(String to, EmailTemplate template, String subject, Map<String, Object> variables) {
        EmailTemplateRenderer.Rendered rendered = renderer.render(template, variables);
        Instant now = Instant.now();

        EmailOutbox email = new EmailOutbox();
        email.setToAddress(to);
        email.setTemplate(template.name());
        email.setSubject(subject);
        email.setHtmlBody(rendered.html());
        email.setTextBody(rendered.text());
        email.setStatus(EmailStatus.PENDING);
        email.setNextAttemptAt(now);
        email.setCreatedAt(now);
        emailOutboxRepository.save(email);

        eventPublisher.publishEvent(new EmailQueuedEvent());
    }
}
