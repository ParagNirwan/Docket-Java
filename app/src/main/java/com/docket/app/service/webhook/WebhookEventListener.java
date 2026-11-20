package com.docket.app.service.webhook;

import com.docket.app.entity.WebhookEndpoint;
import com.docket.app.enums.WebhookEvent;
import com.docket.app.repository.WebhookEndpointRepository;
import com.docket.app.service.AuditRecordedEvent;
import com.docket.app.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Turns audit records into webhook deliveries. Runs just before the action's transaction
// commits, so the delivery row is saved together with the action (a transactional outbox):
// a rolled-back action sends nothing, and a committed one is never lost.
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookService webhookService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onAuditRecorded(AuditRecordedEvent event) {
        if (event.tenantId() == null) {
            return;
        }
        WebhookEvent.forAction(event.action()).ifPresent(webhookEvent -> {
            try {
                dispatch(event, webhookEvent);
            } catch (RuntimeException exception) {
                // Don't let a webhook problem fail the user's action. (A database error while
                // saving the delivery would still roll it back, which is the outbox guarantee.)
                log.error("Could not queue webhook {} for tenant {}", webhookEvent.getCode(), event.tenantId(), exception);
            }
        });
    }

    private void dispatch(AuditRecordedEvent event, WebhookEvent webhookEvent) {
        List<WebhookEndpoint> endpoints = webhookEndpointRepository.findByTenantIdOrderByCreatedAtDesc(event.tenantId())
                .stream()
                .filter(WebhookEndpoint::isActive)
                .filter(endpoint -> endpoint.getEvents() != null && endpoint.getEvents().contains(webhookEvent.getCode()))
                .toList();
        if (endpoints.isEmpty()) {
            return;
        }
        String payload = payload(event, webhookEvent);
        for (WebhookEndpoint endpoint : endpoints) {
            webhookService.enqueueDelivery(endpoint.getId(), webhookEvent.getCode(), payload);
        }
    }

    // Deliberately small: ids, not contents. Receivers call the public API for details,
    // so a leaked webhook payload exposes no document names or text.
    static String payload(AuditRecordedEvent event, WebhookEvent webhookEvent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", event.targetType());
        data.put("targetId", event.targetId() == null ? null : event.targetId().toString());
        data.put("action", event.action().name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", UUID.randomUUID().toString());
        body.put("type", webhookEvent.getCode());
        body.put("tenantId", event.tenantId().toString());
        body.put("actorUserId", event.actorUserId() == null ? null : event.actorUserId().toString());
        body.put("occurredAt", event.occurredAt().toString());
        body.put("data", data);
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize webhook payload", exception);
        }
    }
}
