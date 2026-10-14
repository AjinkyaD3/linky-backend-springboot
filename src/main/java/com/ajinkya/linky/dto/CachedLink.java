package com.ajinkya.linky.dto;

/**
 * Everything the redirect hot path needs to know about a link, small enough to cache.
 * {@code missing} marks a negative-cache entry (code known not to exist).
 */
public record CachedLink(
        Long id,
        String originalUrl,
        boolean active,
        boolean oneTime,
        boolean hasPassword,
        boolean isPrivate,
        Long ownerId,
        Long expiresAtEpochSecond,
        boolean missing,
        java.util.List<RoutingRule> routingRules) {

    public static CachedLink notFound() {
        return new CachedLink(null, null, false, false, false, false, null, null, true, null);
    }

    public boolean isExpired() {
        return expiresAtEpochSecond != null && java.time.Instant.now().getEpochSecond() >= expiresAtEpochSecond;
    }
}
