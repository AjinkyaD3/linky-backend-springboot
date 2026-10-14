package com.ajinkya.linky.service;

import com.ajinkya.linky.entity.Webhook;
import com.ajinkya.linky.entity.WebhookDelivery;
import com.ajinkya.linky.repository.WebhookDeliveryRepository;
import com.ajinkya.linky.repository.WebhookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Short transactions that record the outcome of one delivery attempt. */
@Service
public class WebhookRecorder {

    /** A webhook that fails this many attempts in a row is disabled so a dead endpoint stops costing us work. */
    static final int DISABLE_AFTER_CONSECUTIVE_FAILURES = 20;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookRepository webhooks;

    public WebhookRecorder(WebhookDeliveryRepository deliveries, WebhookRepository webhooks) {
        this.deliveries = deliveries;
        this.webhooks = webhooks;
    }

    @Transactional
    public void success(Long deliveryId, int statusCode) {
        WebhookDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        delivery.setStatus(WebhookDelivery.DELIVERED);
        delivery.setDeliveredAt(LocalDateTime.now());
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(null);
        Webhook webhook = delivery.getWebhook();
        if (webhook.getConsecutiveFailures() != 0) {
            webhook.setConsecutiveFailures(0);
            webhooks.save(webhook);
        }
    }

    /**
     * @param finalAttempt true when no retries remain (the delivery is marked FAILED)
     * @param nextAttempt  when to retry, ignored for a final attempt
     */
    @Transactional
    public void failure(Long deliveryId, Integer statusCode, String error, boolean finalAttempt, LocalDateTime nextAttempt) {
        WebhookDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        delivery.setLastStatusCode(statusCode);
        delivery.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 300)));
        if (finalAttempt) {
            delivery.setStatus(WebhookDelivery.FAILED);
        } else {
            delivery.setNextAttemptAt(nextAttempt);
        }

        Webhook webhook = delivery.getWebhook();
        webhook.setConsecutiveFailures(webhook.getConsecutiveFailures() + 1);
        if (webhook.isActive() && webhook.getConsecutiveFailures() >= DISABLE_AFTER_CONSECUTIVE_FAILURES) {
            webhook.setActive(false);
            webhook.setDisabledReason("Disabled automatically after " + DISABLE_AFTER_CONSECUTIVE_FAILURES
                    + " consecutive failed deliveries. Fix the endpoint, then enable it again.");
        }
        webhooks.save(webhook);
    }

    /** Marks a delivery as permanently failed without counting against the endpoint (e.g. webhook removed or disabled). */
    @Transactional
    public void abandon(Long deliveryId, String reason) {
        deliveries.findById(deliveryId).ifPresent(d -> {
            d.setStatus(WebhookDelivery.FAILED);
            d.setLastError(reason);
        });
    }
}
