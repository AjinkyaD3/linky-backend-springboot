package com.ajinkya.linky.dto;

import jakarta.validation.constraints.Future;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;
import java.util.List;

/** Partial update: fields left null are not changed. */
@Data
public class UpdateUrlRequest {
    private String title;
    private String description;
    private List<String> tags;

    /** Change where the short link points. The short code and its analytics stay the same. */
    @URL(regexp = "^(?i)https?://.*", message = "Must be a valid http or https URL")
    private String originalUrl;

    @Future(message = "Expiry must be in the future")
    private Instant expiresAt;

    /** Set true to remove an existing expiry. */
    private Boolean clearExpiry;

    /** Non-null replaces all routing rules; an empty list removes them. */
    private List<RoutingRule> routingRules;
}
