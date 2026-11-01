package com.docket.app.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Who is on the other end of the current request. Returns null outside a request,
// for example in a scheduled job.
@Component
public class ClientRequestInfo {
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final int trustedProxyHops;

    public ClientRequestInfo(@Value("${docket.security.trusted-proxy-hops}") int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    // Anyone can send a fake X-Forwarded-For, and proxies only ever append to it. So
    // only entries added by our own proxies count, reading from the right. Trusting
    // the leftmost entry would let an attacker dodge rate limits by changing the header.
    public String ip() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (trustedProxyHops > 0 && forwardedFor != null) {
            String[] hops = forwardedFor.split(",");
            if (hops.length >= trustedProxyHops) {
                return hops[hops.length - trustedProxyHops].trim();
            }
        }
        return request.getRemoteAddr();
    }

    public String userAgent() {
        HttpServletRequest request = currentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > MAX_USER_AGENT_LENGTH ? userAgent.substring(0, MAX_USER_AGENT_LENGTH) : userAgent;
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
