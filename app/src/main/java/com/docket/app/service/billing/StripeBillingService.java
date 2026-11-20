package com.docket.app.service.billing;

import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Plan;
import com.docket.app.repository.TenantRepository;
import com.docket.app.service.AuditService;
import com.docket.app.service.TenantAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// A deliberately small Stripe integration (test-mode level, not production-hardened):
// Checkout for upgrades, the Customer Portal for everything else, and a signed webhook that
// copies the subscription's plan and status onto the tenant. Talks to Stripe's REST API
// directly, so there's no SDK dependency.
//
// Not covered (see docs/billing.md): idempotent event storage, proration previews,
// dunning emails, tax, multiple subscriptions per tenant.
@Slf4j
@Service
public class StripeBillingService {
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;
    private final RestClient stripe;
    private final String secretKey;
    private final String webhookSecret;
    private final String priceIdPro;
    private final String priceIdBusiness;
    private final String baseUrl;

    public StripeBillingService(
            TenantRepository tenantRepository,
            TenantAuthorizationService tenantAuthorizationService,
            AuditService auditService,
            @Value("${docket.billing.stripe.secret-key:}") String secretKey,
            @Value("${docket.billing.stripe.webhook-secret:}") String webhookSecret,
            @Value("${docket.billing.stripe.price-pro:}") String priceIdPro,
            @Value("${docket.billing.stripe.price-business:}") String priceIdBusiness,
            @Value("${docket.billing.stripe.api-base:https://api.stripe.com}") String apiBase,
            @Value("${docket.app.base-url}") String baseUrl) {
        this.tenantRepository = tenantRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.auditService = auditService;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.priceIdPro = priceIdPro;
        this.priceIdBusiness = priceIdBusiness;
        this.baseUrl = baseUrl;
        this.stripe = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();
    }

    public boolean isConfigured() {
        return !secretKey.isBlank() && !webhookSecret.isBlank() && !priceIdPro.isBlank();
    }

    public record BillingStatus(String plan, String subscriptionStatus, Instant renewsAt, boolean configured,
                                boolean hasCustomer) {
    }

    public BillingStatus status(String slug, Authentication authentication) {
        Tenant tenant = requireOwner(slug, authentication);
        return new BillingStatus(
                (tenant.getPlan() == null ? Plan.FREE : tenant.getPlan()).name(),
                tenant.getSubscriptionStatus(),
                tenant.getPlanRenewsAt(),
                isConfigured(),
                tenant.getStripeCustomerId() != null);
    }

    @Transactional
    public String checkoutUrl(String slug, Plan plan, Authentication authentication) {
        Tenant tenant = requireOwner(slug, authentication);
        requireConfigured();
        String priceId = priceFor(plan)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That plan can't be bought online"));
        String customerId = ensureCustomer(tenant);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("customer", customerId);
        form.add("client_reference_id", tenant.getId().toString());
        form.add("line_items[0][price]", priceId);
        form.add("line_items[0][quantity]", "1");
        form.add("subscription_data[metadata][tenant_id]", tenant.getId().toString());
        form.add("success_url", workspaceUrl(tenant) + "?tab=billing&checkout=success");
        form.add("cancel_url", workspaceUrl(tenant) + "?tab=billing&checkout=cancelled");
        return post("/v1/checkout/sessions", form).path("url").asText();
    }

    public String portalUrl(String slug, Authentication authentication) {
        Tenant tenant = requireOwner(slug, authentication);
        requireConfigured();
        if (tenant.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There's no subscription to manage yet");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", tenant.getStripeCustomerId());
        form.add("return_url", workspaceUrl(tenant) + "?tab=billing");
        return post("/v1/billing_portal/sessions", form).path("url").asText();
    }

    // ─── Webhook ──────────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (webhookSecret.isBlank() || !validSignature(payload, signatureHeader, webhookSecret, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }
        JsonNode event;
        try {
            event = JSON.readTree(payload);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
        }
        String type = event.path("type").asText();
        JsonNode object = event.path("data").path("object");
        switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    onSubscriptionChanged(object, type.endsWith("deleted"));
            default -> log.debug("Ignoring Stripe event {}", type);
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        findTenant(session.path("client_reference_id").asText(null), session.path("customer").asText(null))
                .ifPresent(tenant -> {
                    tenant.setStripeCustomerId(session.path("customer").asText(tenant.getStripeCustomerId()));
                    tenant.setStripeSubscriptionId(session.path("subscription").asText(tenant.getStripeSubscriptionId()));
                    tenantRepository.save(tenant);
                });
    }

    private void onSubscriptionChanged(JsonNode subscription, boolean deleted) {
        Optional<Tenant> found = findTenant(
                subscription.path("metadata").path("tenant_id").asText(null),
                subscription.path("customer").asText(null));
        if (found.isEmpty()) {
            log.warn("Stripe subscription {} matches no tenant", subscription.path("id").asText());
            return;
        }
        Tenant tenant = found.get();
        String status = subscription.path("status").asText("canceled");
        boolean active = !deleted && ("active".equals(status) || "trialing".equals(status));
        String priceId = subscription.path("items").path("data").path(0).path("price").path("id").asText("");

        Plan before = tenant.getPlan();
        Plan after = active ? planFor(priceId).orElse(Plan.FREE) : Plan.FREE;
        tenant.setPlan(after);
        tenant.setSubscriptionStatus(deleted ? "canceled" : status);
        tenant.setStripeSubscriptionId(deleted ? null : subscription.path("id").asText(null));
        long periodEnd = subscription.path("current_period_end").asLong(0);
        tenant.setPlanRenewsAt(periodEnd > 0 && !deleted ? Instant.ofEpochSecond(periodEnd) : null);
        tenantRepository.save(tenant);

        if (before != after) {
            auditService.recordSystem(tenant.getId(), AuditAction.PLAN_CHANGED, "TENANT", tenant.getId(),
                    Map.of("plan", after.name(), "previousPlan", String.valueOf(before), "source", "stripe"));
        }
    }

    // Stripe-Signature: t=<unix>,v1=<hex hmac of "t.payload">[,v1=...]. Constant-time compare,
    // and a 5-minute window against replayed requests.
    static boolean validSignature(String payload, String header, String secret, Instant now) {
        if (header == null || payload == null) return false;
        long timestamp = -1;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp < 0 || signatures.isEmpty()
                || Math.abs(now.getEpochSecond() - timestamp) > SIGNATURE_TOLERANCE_SECONDS) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(hmac(secret, timestamp + "." + payload)).getBytes(StandardCharsets.UTF_8);
        return signatures.stream().anyMatch(sig -> MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String ensureCustomer(Tenant tenant) {
        if (tenant.getStripeCustomerId() != null) {
            return tenant.getStripeCustomerId();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", tenant.getName());
        form.add("metadata[tenant_id]", tenant.getId().toString());
        String id = post("/v1/customers", form).path("id").asText();
        tenant.setStripeCustomerId(id);
        tenantRepository.save(tenant);
        return id;
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        try {
            String body = stripe.post().uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return JSON.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException exception) {
            log.warn("Stripe {} failed: {} {}", path, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider rejected the request");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't reach the payment provider", exception);
        }
    }

    private Optional<Tenant> findTenant(String tenantId, String customerId) {
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                Optional<Tenant> byId = tenantRepository.findById(UUID.fromString(tenantId));
                if (byId.isPresent()) return byId;
            } catch (IllegalArgumentException ignored) {
                // fall through to the customer lookup
            }
        }
        return customerId == null ? Optional.empty() : tenantRepository.findByStripeCustomerId(customerId);
    }

    private Optional<String> priceFor(Plan plan) {
        return switch (plan) {
            case PRO -> Optional.of(priceIdPro).filter(s -> !s.isBlank());
            case BUSINESS -> Optional.of(priceIdBusiness).filter(s -> !s.isBlank());
            default -> Optional.empty();
        };
    }

    private Optional<Plan> planFor(String priceId) {
        if (!priceId.isBlank() && priceId.equals(priceIdPro)) return Optional.of(Plan.PRO);
        if (!priceId.isBlank() && priceId.equals(priceIdBusiness)) return Optional.of(Plan.BUSINESS);
        return Optional.empty();
    }

    private Tenant requireOwner(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.BILLING_MANAGE);
        return tenant;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Online billing isn't set up on this server");
        }
    }

    private String workspaceUrl(Tenant tenant) {
        return baseUrl + "/workspace/" + tenant.getSlug();
    }
}
