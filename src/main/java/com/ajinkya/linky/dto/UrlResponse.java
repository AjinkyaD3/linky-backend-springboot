package com.ajinkya.linky.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UrlResponse {
    private Long id;
    private String shortCode;
    private String originalUrl;
    private LocalDateTime createdAt;
    private Long clickCount;
    private String title;
    private String description;
    private Boolean isFavorite;
    private Boolean isArchived;
    private String visibility;
    private java.util.List<String> tags;
    private java.time.Instant expiresAt;
    private Boolean isOneTime;
    private Boolean hasPassword;
    private java.util.List<RoutingRule> routingRules;
}
