package com.docket.app.service.billing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeSignatureTest {
    private static final String SECRET = "whsec_test";
    private static final String PAYLOAD = "{\"type\":\"customer.subscription.updated\"}";

    private static String header(long t, String secret, String payload) {
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(StripeBillingService.hmac(secret, t + "." + payload));
    }

    @Test
    void acceptsAFreshCorrectlySignedEvent() {
        long now = Instant.now().getEpochSecond();
        assertTrue(StripeBillingService.validSignature(PAYLOAD, header(now, SECRET, PAYLOAD), SECRET, Instant.now()));
    }

    @Test
    void rejectsForgedTamperedOrReplayedEvents() {
        long now = Instant.now().getEpochSecond();
        assertFalse(StripeBillingService.validSignature(PAYLOAD, header(now, "wrong", PAYLOAD), SECRET, Instant.now()));
        assertFalse(StripeBillingService.validSignature(PAYLOAD + " ", header(now, SECRET, PAYLOAD), SECRET, Instant.now()));
        assertFalse(StripeBillingService.validSignature(PAYLOAD, header(now - 3600, SECRET, PAYLOAD), SECRET, Instant.now()));
        assertFalse(StripeBillingService.validSignature(PAYLOAD, null, SECRET, Instant.now()));
        assertFalse(StripeBillingService.validSignature(PAYLOAD, "t=abc,v1=00", SECRET, Instant.now()));
    }
}
