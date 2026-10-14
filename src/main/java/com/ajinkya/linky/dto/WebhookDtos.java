package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class WebhookDtos {

    private WebhookDtos() {}

    public record CreateRequest(
            @NotBlank(message = "URL is required") @Size(max = 2048, message = "URL is too long") String url,
            @NotEmpty(message = "Choose at least one event") List<String> events) {}

    /** Partial update: null fields are left unchanged. */
    public record UpdateRequest(@Size(max = 2048, message = "URL is too long") String url, List<String> events, Boolean active) {}

    /** {@code secret} is only populated in the response to creating a webhook; it cannot be read again. */
    public record WebhookView(Long id, String url, List<String> events, boolean active, int consecutiveFailures,
                              String disabledReason, LocalDateTime createdAt, String secret) {}

    public record DeliveryView(Long id, String event, String status, int attempts, Integer lastStatusCode,
                               String lastError, LocalDateTime createdAt, LocalDateTime deliveredAt) {}
}
