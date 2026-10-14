package com.ajinkya.linky.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class CreateUrlRequest {
    
    @NotBlank(message = "URL is required")
    @URL(regexp = "^(?i)https?://.*", message = "Must be a valid http or https URL")
    private String originalUrl;

    @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Za-z0-9_-]{3,20}$",
            message = "Alias must be 3-20 characters: letters, digits, dash or underscore")
    private String customAlias;

    private String visibility;

    private String password;

    private Boolean isOneTime;

    /** Optional expiry as an ISO-8601 instant (e.g. 2026-12-31T18:30:00Z). Must be in the future. */
    @Future(message = "Expiry must be in the future")
    private java.time.Instant expiresAt;

    /** Optional conditional redirects (device / os / country). Max 10, first match wins. */
    private java.util.List<RoutingRule> routingRules;
}
