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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    private final MeterRegistry meterRegistry;
    private final com.docket.app.service.webhook.WebhookUrlGuard webhookUrlGuard;

    private RestClient restClient;

    // Metrics
    private Counter deliveriesTotal;
    private Counter deliveriesSucceeded;
    private Counter deliveriesFailed;
    private Counter deliveriesRetried;
    private Timer deliveryLatency;
    private final AtomicInteger pendingDeliveriesGauge = new AtomicInteger(0);
    private final AtomicInteger inProgressDeliveriesGauge = new AtomicInteger(0);

    @PostConstruct
    void init() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) webhookProperties.getHttpTimeout().toMillis());
        factory.setReadTimeout((int) webhookProperties.getHttpTimeout().toMillis());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        // Initialize metrics
        deliveriesTotal = Counter.builder("docket.webhook.deliveries.total")
                .description("Total number of webhook delivery attempts")
                .tag("status", "unknown")
                .register(meterRegistry);
        deliveriesSucceeded = Counter.builder("docket.webhook.deliveries.succeeded")
                .description("Total number of successful webhook deliveries")
                .register(meterRegistry);
        deliveriesFailed = Counter.builder("docket.webhook.deliveries.failed")
                .description("Total number of failed webhook deliveries (moved to DLQ)")
                .register(meterRegistry);
        deliveriesRetried = Counter.builder("docket.webhook.deliveries.retried")
                .description("Total number of webhook deliveries scheduled for retry")
                .register(meterRegistry);
        deliveryLatency = Timer.builder("docket.webhook.delivery.latency")
                .description("Latency of webhook delivery HTTP calls")
                .register(meterRegistry);

        // Register gauges for pending and in-progress deliveries
        Gauge.builder("docket.webhook.deliveries.pending", pendingDeliveriesGauge, AtomicInteger::get)
                .description("Number of pending webhook deliveries")
                .register(meterRegistry);
        Gauge.builder("docket.webhook.deliveries.in_progress", inProgressDeliveriesGauge, AtomicInteger::get)
                .description("Number of in-progress webhook deliveries")
                .register(meterRegistry);
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
        endpoint.setUrl(webhookUrlGuard.validate(request.url()));
        endpoint.setSecret(com.docket.app.security.TokenHasher.newToken());
        endpoint.setEvents(com.docket.app.enums.WebhookEvent.normalize(request.events()));
        endpoint.setCreatedAt(Instant.now());
        endpoint.setActive(true);
        webhookEndpointRepository.save(endpoint);

        auditService.record(authentication, tenant.getId(), AuditAction.WEBHOOK_CREATED, "WEBHOOK_ENDPOINT", endpoint.getId(),
                Map.of("url", endpoint.getUrl()));
        return toDto(endpoint, endpoint.getSecret());
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
        pendingDeliveriesGauge.incrementAndGet();
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

        // Update gauges: pending -> in_progress
        pendingDeliveriesGauge.addAndGet(-deliveries.size());
        inProgressDeliveriesGauge.addAndGet(deliveries.size());

        return deliveries;
    }

    protected void processSingleDelivery(WebhookDelivery delivery, int maxRetries) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        String payload = delivery.getPayload();
        String eventType = delivery.getEventType();

        // Re-checked at send time: the host may resolve somewhere else than when it was saved.
        if (!webhookUrlGuard.isAllowed(endpoint.getUrl())) {
            handleDeliveryFailure(delivery, maxRetries, "Webhook URL now resolves to a blocked address");
            return;
        }

        String signature;
        try {
            signature = computeHmacSha256(endpoint.getSecret(), payload);
        } catch (Exception e) {
            log.error("Failed to compute webhook signature for delivery {}: {}", delivery.getId(), e.getMessage());
            handleDeliveryFailure(delivery, maxRetries, "Signature computation failed: " + e.getMessage());
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            var response = restClient.post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Docket-Event", eventType)
                    .header("X-Docket-Signature", "sha256=" + signature)
                    .body(isSlack(endpoint.getUrl()) ? slackMessage(eventType, payload) : payload)
                    .retrieve()
                    .toBodilessEntity();

            // Success - update in a new short transaction
            sample.stop(deliveryLatency);
            deliveriesTotal.increment();
            deliveriesSucceeded.increment();
            markDelivered(delivery, response.getStatusCode().value());

        } catch (Exception ex) {
            sample.stop(deliveryLatency);
            deliveriesTotal.increment();
            handleDeliveryFailure(delivery, maxRetries, ex.getMessage());
        } finally {
            // Update gauges: in_progress -> (decremented in handleDeliveryFailure or markDelivered)
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
        // Update gauge: in_progress decremented
        inProgressDeliveriesGauge.decrementAndGet();
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
            deliveriesFailed.increment();
            inProgressDeliveriesGauge.decrementAndGet();
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
            deliveriesRetried.increment();
            inProgressDeliveriesGauge.decrementAndGet();
            pendingDeliveriesGauge.incrementAndGet();
        }
        webhookDeliveryRepository.save(managed);
    }

    // Slack incoming webhooks (https://hooks.slack.com/services/…) only accept {"text": …},
    // so those endpoints get a readable message instead of the raw event.
    static boolean isSlack(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null && host.equalsIgnoreCase("hooks.slack.com");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String slackMessage(String eventType, String payload) {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        String target = "";
        try {
            var data = json.readTree(payload).path("data");
            String type = data.path("targetType").asText("");
            String id = data.path("targetId").asText("");
            target = type.isEmpty() ? "" : " (" + type.toLowerCase(java.util.Locale.ROOT) + " " + id + ")";
        } catch (Exception ignored) {
            // fall back to the bare event name
        }
        String label = switch (eventType) {
            case "document.uploaded" -> ":page_facing_up: A document was uploaded";
            case "document.updated" -> ":pencil2: A document was updated";
            case "document.deleted" -> ":wastebasket: A document was deleted";
            case "document.restored" -> ":recycle: A document was restored";
            case "member.joined" -> ":wave: Someone joined the organization";
            case "member.removed" -> ":door: A member left or was removed";
            case "member.role_changed" -> ":key: A member's role changed";
            default -> "Docket event " + eventType;
        };
        try {
            return json.writeValueAsString(java.util.Map.of("text", label + target));
        } catch (Exception exception) {
            return "{\"text\":\"Docket event\"}";
        }
    }

    private String computeHmacSha256(String secret, String payload) throws Exception {
        // Derive a proper HMAC key from the secret using HKDF-SHA256
        // This ensures the key has full entropy regardless of the secret format (UUID, etc.)
        byte[] derivedKey = hkdfSha256(
                secret.getBytes(StandardCharsets.UTF_8),
                "docket-webhook-hmac".getBytes(StandardCharsets.UTF_8),
                32
        );

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(derivedKey, "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    // HKDF-SHA256 (RFC 5869) - extract + expand
    private byte[] hkdfSha256(byte[] ikm, byte[] info, int length) throws Exception {
        // Extract: PRK = HMAC-SHA256(salt, IKM)
        // Using empty salt as per RFC 5869 when no salt is provided
        byte[] salt = new byte[32]; // 32 zero bytes for SHA-256
        Mac extractMac = Mac.getInstance("HmacSHA256");
        extractMac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = extractMac.doFinal(ikm);

        // Expand: T = HMAC-SHA256(PRK, T_previous || info || counter)
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int counter = 1;
        int pos = 0;

        Mac expandMac = Mac.getInstance("HmacSHA256");
        expandMac.init(new SecretKeySpec(prk, "HmacSHA256"));

        while (pos < length) {
            expandMac.update(t);
            expandMac.update(info);
            expandMac.update((byte) counter);
            t = expandMac.doFinal();

            int remaining = length - pos;
            int toCopy = Math.min(t.length, remaining);
            System.arraycopy(t, 0, okm, pos, toCopy);
            pos += toCopy;
            counter++;
        }

        return okm;
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private WebhookEndpointDTO toDto(WebhookEndpoint endpoint) {
        return toDto(endpoint, null);
    }

    private WebhookEndpointDTO toDto(WebhookEndpoint endpoint, String signingSecret) {
        return new WebhookEndpointDTO(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.getEvents() == null ? List.of() : endpoint.getEvents(),
                endpoint.isActive(),
                endpoint.getCreatedAt(),
                signingSecret
        );
    }
}