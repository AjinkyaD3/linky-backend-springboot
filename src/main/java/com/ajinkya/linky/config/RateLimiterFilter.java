package com.ajinkya.linky.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

public class RateLimiterFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;
    
    // Allow 10 requests per minute per IP for the creation API
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimiterFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        // Only rate limit URL creation API
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/url")) {
            String clientIp = extractIpAddress(request);
            String redisKey = "rate_limit:" + clientIp;
            
            Long requests = redisTemplate.opsForValue().increment(redisKey);
            
            if (requests != null && requests == 1) {
                // First request, set expiration
                redisTemplate.expire(redisKey, WINDOW);
            }
            
            if (requests != null && requests > MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Too many requests. Please try again later.");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
