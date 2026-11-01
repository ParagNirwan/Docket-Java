package com.docket.app.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientRequestInfoTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void withNoTrustedProxiesTheHeaderIsIgnored() {
        request("1.1.1.1");

        assertEquals("10.0.0.9", new ClientRequestInfo(0).ip());
    }

    @Test
    void aFakedHeaderCantHideTheRealClient() {
        // The client sent "6.6.6.6", then our one proxy appended the address it really saw.
        request("6.6.6.6, 203.0.113.7");

        assertEquals("203.0.113.7", new ClientRequestInfo(1).ip());
    }

    @Test
    void behindAGoogleLoadBalancerTheClientIsSecondFromTheRight() {
        // Google appends "client, load balancer".
        request("6.6.6.6, 203.0.113.7, 34.1.2.3");

        assertEquals("203.0.113.7", new ClientRequestInfo(2).ip());
    }

    @Test
    void aShorterHeaderThanExpectedFallsBackToTheConnection() {
        request("6.6.6.6");

        assertEquals("10.0.0.9", new ClientRequestInfo(2).ip());
    }

    @Test
    void outsideARequestThereIsNoAddress() {
        assertNull(new ClientRequestInfo(1).ip());
    }
}
