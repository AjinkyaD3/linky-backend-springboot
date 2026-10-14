package com.ajinkya.linky.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {}

    /**
     * Resolves the caller's IP without trusting spoofable headers.
     *
     * Each proxy appends the address it saw to X-Forwarded-For, so only the entries written by
     * our own proxies can be trusted: counting from the right, the {@code trustedProxyHops}-th
     * entry is the real client. Anything a client put further left is ignored. With 0 hops the
     * header is ignored entirely and the socket address is used.
     */
    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        if (trustedProxyHops > 0) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                int idx = parts.length - trustedProxyHops;
                if (idx >= 0) {
                    return parts[idx].trim();
                }
            }
        }
        return request.getRemoteAddr();
    }
}
