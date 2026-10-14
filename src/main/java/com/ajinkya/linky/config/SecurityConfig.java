package com.ajinkya.linky.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Value("${app.cors-allowed-origins}")
    private String corsAllowedOrigins;

    @Value("${app.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @Value("${app.redis-key-prefix:}")
    private String redisKeyPrefix;

    @Value("${app.metrics-token:}")
    private String metricsToken;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RedisTemplate<String, String> redisTemplate, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.redisTemplate = redisTemplate;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        // Add our rate limiter filter before the JWT filter
        RateLimiterFilter rateLimiterFilter = new RateLimiterFilter(redisTemplate, trustedProxyHops, redisKeyPrefix);
        
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                // The API serves JSON and redirects, never embeddable UI.
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(63072000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; img-src 'self' data:"))
                .permissionsPolicyHeader(permissions -> permissions.policy(
                        "camera=(), microphone=(), geolocation=()")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // 404 unless API_DOCS_ENABLED
                .requestMatchers(HttpMethod.GET, "/api/public/pages/*").permitAll()
                .requestMatchers("/actuator/info").permitAll() // commit sha only; lets deploys verify the live build
                // Prometheus scrape endpoint: closed unless a shared token is configured and presented.
                .requestMatchers("/actuator/prometheus").access((authentication, context) ->
                        new org.springframework.security.authorization.AuthorizationDecision(
                                metricsTokenMatches(context.getRequest().getHeader("X-Metrics-Token"))))
                // Deliberate: links created anonymously have no owner, so their
                // analytics are readable by anyone holding the short code. Owned
                // links are still scoped to the owner inside AnalyticsService -
                // see AnalyticsControllerTest for the IDOR regression test.
                .requestMatchers("/api/analytics/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers(org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher("^/[a-zA-Z0-9_-]+(?:/unlock)?$")).permitAll()
                .requestMatchers("/api/url/*/qrcode").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/url").permitAll() // Anonymous URL creation
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    private boolean metricsTokenMatches(String presented) {
        if (metricsToken == null || metricsToken.isBlank() || presented == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                metricsToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                presented.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", "X-API-KEY"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
