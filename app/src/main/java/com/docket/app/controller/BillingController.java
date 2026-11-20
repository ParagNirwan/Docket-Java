package com.docket.app.controller;

import com.docket.app.enums.Plan;
import com.docket.app.service.billing.StripeBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BillingController {
    private final StripeBillingService billingService;

    public record CheckoutRequest(Plan plan) {
    }

    @GetMapping("/api/tenants/{slug}/billing")
    public ResponseEntity<StripeBillingService.BillingStatus> status(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(billingService.status(slug, authentication));
    }

    @PostMapping("/api/tenants/{slug}/billing/checkout")
    public ResponseEntity<Map<String, String>> checkout(@PathVariable String slug, @RequestBody CheckoutRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(Map.of("url", billingService.checkoutUrl(slug, request.plan(), authentication)));
    }

    @PostMapping("/api/tenants/{slug}/billing/portal")
    public ResponseEntity<Map<String, String>> portal(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(Map.of("url", billingService.portalUrl(slug, authentication)));
    }

    // Public: Stripe calls this. The Stripe-Signature header is what authenticates it.
    @PostMapping("/api/billing/stripe/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        billingService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
