package com.ajinkya.linky.config;

import com.ajinkya.linky.entity.ApiKey;
import com.ajinkya.linky.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Optional;
import java.util.Collections;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyHeader = request.getHeader("X-API-KEY");

        if (apiKeyHeader != null && apiKeyHeader.startsWith("lk_")) {
            String[] parts = apiKeyHeader.split("_");
            if (parts.length == 3) {
                try {
                    Long id = Long.parseLong(parts[1]);
                    Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(id);
                    
                    if (apiKeyOpt.isPresent()) {
                        ApiKey apiKey = apiKeyOpt.get();
                        if (passwordEncoder.matches(apiKeyHeader, apiKey.getKeyValue())) {
                            // Set the user in the security context
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    apiKey.getUser(), null, Collections.emptyList()); // Add roles later if needed
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid ID format, ignore and let filter chain handle unauthorized
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
