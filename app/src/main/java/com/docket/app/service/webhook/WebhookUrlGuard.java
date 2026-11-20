package com.docket.app.service.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

// Webhooks make the server send requests to an address an admin chose. Without this guard
// an admin could aim them at the cloud metadata service or other internal hosts (SSRF).
// Checked when the endpoint is saved and again before each delivery, because DNS can change.
@Component
public class WebhookUrlGuard {
    private final boolean allowPrivateTargets;

    public WebhookUrlGuard(@Value("${docket.webhooks.allow-private-targets:false}") boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    // Returns the normalized URL, or throws 400 with a reason an admin can act on.
    public String validate(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw bad("Enter a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getUserInfo() != null) {
            throw bad("Enter a valid URL such as https://example.com/hooks/docket");
        }
        if (!scheme.equals("https") && !(allowPrivateTargets && scheme.equals("http"))) {
            throw bad("Webhook URLs must use https");
        }
        if (!allowPrivateTargets) {
            requirePublicHost(host);
        }
        return uri.toString();
    }

    public boolean isAllowed(String url) {
        try {
            validate(url);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private static void requirePublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw bad("Couldn't resolve " + host);
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw bad("Webhook URLs can't point at private or internal addresses");
            }
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            // fc00::/7 unique local
            return (bytes[0] & 0xfe) == 0xfc;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        // 100.64.0.0/10 carrier-grade NAT, 0.0.0.0/8
        return (first == 100 && second >= 64 && second <= 127) || first == 0;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
