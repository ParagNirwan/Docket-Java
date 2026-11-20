package com.docket.app.service.webhook;

import com.docket.app.entity.WebhookEndpoint;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.WebhookEvent;
import com.docket.app.repository.WebhookEndpointRepository;
import com.docket.app.service.AuditRecordedEvent;
import com.docket.app.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDispatchTest {
    @Mock private WebhookEndpointRepository webhookEndpointRepository;
    @Mock private WebhookService webhookService;

    @InjectMocks
    private WebhookEventListener listener;

    private final UUID tenantId = UUID.randomUUID();

    private WebhookEndpoint endpoint(boolean active, String... events) {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setTenantId(tenantId);
        endpoint.setActive(active);
        endpoint.setEvents(List.of(events));
        return endpoint;
    }

    private AuditRecordedEvent event(AuditAction action) {
        return new AuditRecordedEvent(tenantId, UUID.randomUUID(), action, "DOCUMENT", UUID.randomUUID(), Instant.now());
    }

    @Test
    void queuesADeliveryForEachActiveSubscribedEndpoint() {
        WebhookEndpoint subscribed = endpoint(true, "document.uploaded");
        WebhookEndpoint otherEvent = endpoint(true, "member.joined");
        WebhookEndpoint paused = endpoint(false, "document.uploaded");
        when(webhookEndpointRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(subscribed, otherEvent, paused));

        listener.onAuditRecorded(event(AuditAction.DOCUMENT_UPLOADED));

        verify(webhookService).enqueueDelivery(eq(subscribed.getId()), eq("document.uploaded"), anyString());
        verify(webhookService, never()).enqueueDelivery(eq(otherEvent.getId()), any(), any());
        verify(webhookService, never()).enqueueDelivery(eq(paused.getId()), any(), any());
    }

    @Test
    void ignoresActionsThatAreNotWebhookEvents() {
        listener.onAuditRecorded(event(AuditAction.DOCUMENT_VIEWED));

        verifyNoInteractions(webhookEndpointRepository, webhookService);
    }

    @Test
    void payloadCarriesIdsNotContent() {
        String payload = WebhookEventListener.payload(event(AuditAction.DOCUMENT_DELETED), WebhookEvent.DOCUMENT_DELETED);

        assertTrue(payload.contains("\"type\":\"document.deleted\""));
        assertTrue(payload.contains("\"tenantId\":\"" + tenantId + "\""));
    }

    @Test
    void eventNamesAreValidated() {
        assertEquals(List.of("document.uploaded"), WebhookEvent.normalize(List.of(" Document.Uploaded ")));
        assertThrows(ResponseStatusException.class, () -> WebhookEvent.normalize(List.of("document.created")));
        assertThrows(ResponseStatusException.class, () -> WebhookEvent.normalize(List.of()));
    }

    @Test
    void urlGuardBlocksInternalTargets() throws Exception {
        WebhookUrlGuard guard = new WebhookUrlGuard(false);

        assertThrows(ResponseStatusException.class, () -> guard.validate("http://example.com/hook"));
        assertThrows(ResponseStatusException.class, () -> guard.validate("https://127.0.0.1/hook"));
        assertThrows(ResponseStatusException.class, () -> guard.validate("https://169.254.169.254/latest/meta-data"));
        assertThrows(ResponseStatusException.class, () -> guard.validate("https://10.0.0.5/hook"));
        assertThrows(ResponseStatusException.class, () -> guard.validate("https://user:pw@example.com/hook"));
        assertTrue(WebhookUrlGuard.isInternal(InetAddress.getByName("100.64.1.1")));
        assertFalse(WebhookUrlGuard.isInternal(InetAddress.getByName("93.184.216.34")));
    }
}
