package com.docket.app.service;

import com.docket.app.config.DocketWebhookProperties;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    private final TenantRepository tenantRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;
    private final DocketWebhookProperties webhookProperties;

    private RestClient restClient;

    @PostConstruct
    void init() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) webhookProperties.getHttpTimeout().toMillis());
        factory.setReadTimeout((int) webhookProperties.getHttpTimeout().toMillis());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

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
        delivery.setStatus(WebhookDelivery.Status.PENDING.name());
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(Instant.now());
        webhookDeliveryRepository.save(delivery);
    }

    @Scheduled(fixedDelayString = "${docket.webhooks.retry-delay-ms:60000}")
    public void processPendingDeliveries() {
        int batchSize = webhookProperties.getBatchSize();
        int maxRetries = webhookProperties.getMaxRetries();

        // Use a separate transaction just to fetch and lock the batch
        List<WebhookDelivery> pendingDeliveries = fetchAndLockBatch(batchSize);

        if (pendingDeliveries.isEmpty()) {
            return;
        }

        // Process each delivery WITHOUT holding a DB transaction
        for (WebhookDelivery delivery : pendingDeliveries) {
            processSingleDelivery(delivery, maxRetries);
        }
    }

    @Transactional
    protected List<WebhookDelivery> fetchAndLockBatch(int batchSize) {
        // Fetch pending deliveries that are due for retry
        List<WebhookDelivery> deliveries = webhookDeliveryRepository
                .findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        WebhookDelivery.Status.PENDING.name(),
                        Instant.now()
                )
                .stream()
                .limit(batchSize)
                .collect(Collectors.toList());

        // Mark as "IN_PROGRESS" to prevent other workers from picking them up
        // (optional: could use SELECT FOR UPDATE SKIP LOCKED if supported)
        for (WebhookDelivery delivery : deliveries) {
            delivery.setStatus("IN_PROGRESS");
            webhookDeliveryRepository.save(delivery);
        }

        return deliveries;
    }

    protected void processSingleDelivery(WebhookDelivery delivery, int maxRetries) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        String payload = delivery.getPayload();
        String eventType = delivery.getEventType();

        String signature;
        try {
            signature = computeHmacSha256(endpoint.getSecret(), payload);
        } catch (Exception e) {
            log.error("Failed to compute webhook signature for delivery {}: {}", delivery.getId(), e.getMessage());
            handleDeliveryFailure(delivery, maxRetries, "Signature computation failed: " + e.getMessage());
            return;
        }

        try {
            var response = restClient.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Docket-Event", eventType)
                    .header("X-Docket-Signature", "sha256=" + signature)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            // Success - update in a new short transaction
            markDelivered(delivery, response.getStatusCode().value());

        } catch (Exception ex) {
            handleDeliveryFailure(delivery, maxRetries, ex.getMessage());
        }
    }

    @Transactional
    protected void markDelivered(WebhookDelivery delivery, int responseStatus) {
        WebhookDelivery managed = webhookDeliveryRepository.findById(delivery.getId()).orElse(null);
        if (managed != null) {
            managed.setStatus(WebhookDelivery.Status.DELIVERED.name());
            managed.setDeliveredAt(Instant.now());
            managed.setResponseStatus(responseStatus);
            webhookDeliveryRepository.save(managed);
        }
    }

    @Transactional
    protected void handleDeliveryFailure(WebhookDelivery delivery, int maxRetries, String errorMessage) {
        WebhookDelivery managed = webhookDeliveryRepository.findById(delivery.getId()).orElse(null);
        if (managed == null) {
            return;
        }

        int nextAttempt = managed.getAttempts() + 1;

        if (nextAttempt >= maxRetries) {
            // Max retries reached - move to FAILED (DLQ)
            managed.setStatus(WebhookDelivery.Status.FAILED.name());
            managed.setLastError("Max retries (" + maxRetries + ") exceeded. Last error: " + errorMessage);
            managed.setAttempts(nextAttempt);
            log.warn("Webhook delivery {} moved to FAILED after {} attempts: {}", delivery.getId(), maxRetries, errorMessage);
        } else {
            // Schedule retry with exponential backoff
            managed.setAttempts(nextAttempt);
            long delayMs = Math.min(
                    webhookProperties.getRetryDelayMs() * (long) Math.pow(2, nextAttempt - 1),
                    webhookProperties.getMaxRetryDelayMs()
            );
            managed.setNextAttemptAt(Instant.now().plusMillis(delayMs));
            managed.setLastError(errorMessage);
            managed.setStatus(WebhookDelivery.Status.PENDING.name());
            log.debug("Webhook delivery {} scheduled for retry {}/{}, next attempt at {}",
                    delivery.getId(), nextAttempt, maxRetries, managed.getNextAttemptAt());
        }
        webhookDeliveryRepository.save(managed);
    }

    private String computeHmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
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