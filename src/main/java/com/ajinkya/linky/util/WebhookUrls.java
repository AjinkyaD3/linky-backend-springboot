package com.ajinkya.linky.util;

import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF guard for user-supplied webhook URLs. A webhook makes the server issue requests on a user's
 * behalf, so without this a user could point one at cloud metadata endpoints (169.254.169.254),
 * localhost services or the internal network.
 *
 * Checks the scheme (https only, unless explicitly relaxed for local development) and resolves the
 * host, rejecting loopback, private, link-local, carrier-grade NAT, unique-local and multicast
 * addresses. It runs when the webhook is saved AND again before every delivery, which narrows (but
 * cannot fully close) the DNS-rebinding window; deliveries also never follow redirects.
 */
public final class WebhookUrls {

    private WebhookUrls() {}

    public static URI validate(String raw, boolean allowInsecure) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) {
            throw new IllegalArgumentException("Webhook URL is required (max 2048 characters)");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Webhook URL is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowInsecure && scheme.equals("http"))) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Webhook URL must have a host and no embedded credentials");
        }
        if (!allowInsecure) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(uri.getHost());
            } catch (Exception e) {
                throw new IllegalArgumentException("Webhook host could not be resolved");
            }
            for (InetAddress address : addresses) {
                if (isInternal(address)) {
                    throw new IllegalArgumentException("Webhook URL must point to a public address");
                }
            }
        }
        return uri;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            return first == 0                                   // "this" network
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT 100.64.0.0/10
                    || (first == 192 && second == 0 && (b[2] & 0xFF) == 0) // 192.0.0.0/24
                    || first >= 240;                            // reserved / broadcast
        }
        // IPv6: unique local fc00::/7; IPv4-mapped addresses are already unwrapped by InetAddress
        return (b[0] & 0xFE) == 0xFC;
    }
}
