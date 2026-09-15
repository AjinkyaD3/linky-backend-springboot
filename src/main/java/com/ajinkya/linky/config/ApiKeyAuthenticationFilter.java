package com.ajinkya.linky.config;

import com.ajinkya.linky.entity.ApiKey;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.ApiKeyRepository;
import com.ajinkya.linky.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
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
                            // apiKey.getUser() is a lazy proxy tied to the (short-lived) query that
                            // loaded it; fetch a fully-initialized User so it can be used as the
                            // principal beyond this filter (e.g. serialized as a response body)
                            // without a LazyInitializationException once the session closes.
                            User user = userRepository.findById(apiKey.getUser().getId()).orElse(null);
                            if (user != null) {
                                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        user, null, Collections.emptyList()); // Add roles later if needed
                                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
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
