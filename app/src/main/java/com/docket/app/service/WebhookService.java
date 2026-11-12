package com.docket.app.service;

import com.docket.app.dto.CreateWebhookRequestDTO;
import com.docket.app.dto.WebhookEndpointDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.WebhookDelivery;
import com.docket.app.entity.WebhookEndpoint;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.WebhookDeliveryRepository;
import com.docket.app.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private final TenantRepository tenantRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;
    private final org.springframework.web.client.RestClient restClient = org.springframework.web.client.RestClient.builder().build();

    public List<WebhookEndpointDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        return webhookEndpointRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public WebhookEndpointDTO create(String slug, CreateWebhookRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenant.getId());
        endpoint.setUrl(request.url().trim());
        endpoint.setSecret(UUID.randomUUID().toString().replace("-", ""));
        endpoint.setEvents(request.events() == null ? List.of() : request.events().stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        endpoint.setCreatedAt(Instant.now());
        endpoint.setActive(true);
        webhookEndpointRepository.save(endpoint);

        auditService.record(authentication, tenant.getId(), AuditAction.WEBHOOK_CREATED, "WEBHOOK_ENDPOINT", endpoint.getId(),
                Map.of("url", endpoint.getUrl()));
        return toDto(endpoint);
    }

    @Transactional
    public WebhookEndpointDTO update(String slug, UUID endpointId, boolean active, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        WebhookEndpoint endpoint = webhookEndpointRepository.findByTenantIdAndId(tenant.getId(), endpointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));
        endpoint.setActive(active);
        webhookEndpointRepository.save(endpoint);
        auditService.record(authentication, tenant.getId(), AuditAction.WEBHOOK_UPDATED, "WEBHOOK_ENDPOINT", endpointId,
                Map.of("active", String.valueOf(active)));
        return toDto(endpoint);
    }

    @Transactional
    public void delete(String slug, UUID endpointId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
        WebhookEndpoint endpoint = webhookEndpointRepository.findByTenantIdAndId(tenant.getId(), endpointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));
        webhookEndpointRepository.delete(endpoint);
        auditService.record(authentication, tenant.getId(), AuditAction.WEBHOOK_DELETED, "WEBHOOK_ENDPOINT", endpointId, Map.of());
    }

    @Transactional
    public void enqueueDelivery(UUID endpointId, String eventType, String payload) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEndpoint(endpoint);
        delivery.setEventType(eventType);
        delivery.setPayload(payload);
        delivery.setStatus("PENDING");
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(Instant.now());
        webhookDeliveryRepository.save(delivery);
    }

    @Scheduled(fixedDelayString = "${docket.webhooks.retry-delay-ms:60000}")
    @Transactional
    public void processPendingDeliveries() {
        for (WebhookDelivery delivery : webhookDeliveryRepository.findByStatusOrderByCreatedAtAsc("PENDING")) {
            if (delivery.getNextAttemptAt() != null && delivery.getNextAttemptAt().isAfter(Instant.now())) {
                continue;
            }
            try {
                var response = restClient.post()
                        .uri(delivery.getEndpoint().getUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Docket-Event", delivery.getEventType())
                        .header("X-Docket-Signature", "sha256=" + Integer.toHexString(delivery.getPayload().hashCode()))
                        .body(delivery.getPayload())
                        .retrieve()
                        .toBodilessEntity();
                delivery.setStatus("DELIVERED");
                delivery.setDeliveredAt(Instant.now());
                delivery.setResponseStatus(response.getStatusCode().value());
            } catch (Exception ex) {
                delivery.setStatus("PENDING");
                delivery.setAttempts(delivery.getAttempts() + 1);
                delivery.setNextAttemptAt(Instant.now().plusSeconds(Math.min(60L * (long) Math.pow(2, delivery.getAttempts()), 3600L)));
                delivery.setLastError(ex.getMessage());
            }
            webhookDeliveryRepository.save(delivery);
        }
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private WebhookEndpointDTO toDto(WebhookEndpoint endpoint) {
        return new WebhookEndpointDTO(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.getEvents() == null ? List.of() : endpoint.getEvents(),
                endpoint.isActive(),
                endpoint.getCreatedAt()
        );
    }
}
