package com.ajinkya.linky.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Fixed-window, per-IP rate limiter backed by Redis.
 *
 * The counter increment and its TTL are set in one Lua script, so a crash between the two
 * can't leave a counter that never expires. Abuse-prone endpoints (link creation, login,
 * registration, password reset, link unlock) each get their own bucket.
 */
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimiterFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String bucket = bucketFor(request);
        if (bucket != null) {
            String key = "rate_limit:" + bucket + ":" + extractIpAddress(request);
            Long requests = redisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(WINDOW_SECONDS));

            if (requests != null && requests > MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"Too Many Requests\","
                                + "\"message\":\"Too many requests. Please try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Returns the bucket name for rate-limited requests, or null if the request is not limited. */
    private String bucketFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/url")) return "create";
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")
                || uri.equals("/api/auth/forgot-password") || uri.equals("/api/auth/reset-password")) {
            return "auth";
        }
        if (uri.endsWith("/unlock")) return "unlock";
        return null;
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
