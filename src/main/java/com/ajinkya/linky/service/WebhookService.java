package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.WebhookDtos.*;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.entity.Webhook;
import com.ajinkya.linky.entity.WebhookDelivery;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.WebhookDeliveryRepository;
import com.ajinkya.linky.repository.WebhookRepository;
import com.ajinkya.linky.util.WebhookUrls;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Webhook management and event publishing.
 *
 * Publishing writes a row to the delivery outbox; {@link WebhookDispatcher} delivers it later with
 * retries. Publishing never throws: a webhook problem must not break the action that triggered it.
 */
@Service
public class WebhookService {

    public static final String LINK_CREATED = "link.created";
    public static final String LINK_MILESTONE = "link.milestone";
    public static final String TEST = "webhook.test";

    /** Events a user may subscribe to ({@link #TEST} is internal). */
    public static final Set<String> SUBSCRIBABLE = Set.of(LINK_CREATED, LINK_MILESTONE);
    static final int MAX_WEBHOOKS_PER_USER = 5;

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper mapper;
    private final boolean allowInsecure;
    private final String publicBaseUrl;

    public WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, ObjectMapper mapper,
                          @Value("${app.webhooks.allow-insecure:false}") boolean allowInsecure,
                          @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.mapper = mapper;
        this.allowInsecure = allowInsecure;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    // ---- management

    @Transactional(readOnly = true)
    public List<WebhookView> list(User user) {
        return webhooks.findByUserOrderByCreatedAtDesc(user).stream().map(w -> view(w, false)).toList();
    }

    @Transactional
    public WebhookView create(User user, CreateRequest request) {
        if (webhooks.countByUser(user) >= MAX_WEBHOOKS_PER_USER) {
            throw new IllegalArgumentException("You can have at most " + MAX_WEBHOOKS_PER_USER + " webhooks");
        }
        Webhook webhook = new Webhook();
        webhook.setUser(user);
        webhook.setUrl(WebhookUrls.validate(request.url(), allowInsecure).toString());
        webhook.setEvents(String.join(",", validEvents(request.events())));
        webhook.setSecret("whsec_" + newSecret());
        webhook.setActive(true);
        return view(webhooks.save(webhook), true);
    }

    @Transactional
    public WebhookView update(User user, Long id, UpdateRequest request) {
        Webhook webhook = owned(user, id);
        if (request.url() != null) {
            webhook.setUrl(WebhookUrls.validate(request.url(), allowInsecure).toString());
        }
        if (request.events() != null) {
            webhook.setEvents(String.join(",", validEvents(request.events())));
        }
        if (request.active() != null) {
            webhook.setActive(request.active());
            if (request.active()) { // re-enabling is a fresh start
                webhook.setConsecutiveFailures(0);
                webhook.setDisabledReason(null);
            }
        }
        return view(webhooks.save(webhook), false);
    }

    @Transactional
    public void delete(User user, Long id) {
        webhooks.delete(owned(user, id));
    }

    /** Queues a signed {@code webhook.test} ping so users can check their endpoint. */
    @Transactional
    public void sendTest(User user, Long id) {
        Webhook webhook = owned(user, id);
        if (!webhook.isActive()) {
            throw new IllegalArgumentException("Webhook is disabled; enable it first");
        }
        enqueue(webhook, TEST, Map.of("message", "This is a test event from Linky"));
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> recentDeliveries(User user, Long id) {
        Webhook webhook = owned(user, id);
        return deliveries.findByWebhookOrderByIdDesc(webhook, PageRequest.of(0, 20)).stream()
                .map(d -> new DeliveryView(d.getId(), d.getEvent(), d.getStatus(), d.getAttempts(),
                        d.getLastStatusCode(), d.getLastError(), d.getCreatedAt(), d.getDeliveredAt()))
                .toList();
    }

    // ---- publishing

    /** Queues {@code event} for each of the owner's active webhooks subscribed to it. Never throws. */
    public void publish(User owner, String event, Map<String, Object> data) {
        if (owner == null) {
            return;
        }
        try {
            for (Webhook webhook : webhooks.findByUserAndActiveTrue(owner)) {
                if (webhook.subscribesTo(event)) {
                    enqueue(webhook, event, data);
                }
            }
        } catch (Exception e) {
            log.warn("Could not queue webhook event {}: {}", event, e.toString());
        }
    }

    public Map<String, Object> linkData(String shortCode, String originalUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shortCode", shortCode);
        data.put("shortUrl", publicBaseUrl + "/" + shortCode);
        data.put("originalUrl", originalUrl);
        return data;
    }

    private void enqueue(Webhook webhook, String event, Map<String, Object> data) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", UUID.randomUUID().toString());
            envelope.put("event", event);
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("data", data);

            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWebhook(webhook);
            delivery.setEvent(event);
            delivery.setPayload(mapper.writeValueAsString(envelope));
            delivery.setNextAttemptAt(LocalDateTime.now());
            deliveries.save(delivery);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise webhook payload", e);
        }
    }

    // ---- helpers

    private Webhook owned(User user, Long id) {
        return webhooks.findById(id)
                .filter(w -> w.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    }

    private static List<String> validEvents(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one event");
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String event : requested) {
            if (event == null || !SUBSCRIBABLE.contains(event.trim())) {
                throw new IllegalArgumentException("Unknown event. Choose from " + SUBSCRIBABLE);
            }
            clean.add(event.trim());
        }
        return List.copyOf(clean);
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static WebhookView view(Webhook w, boolean includeSecret) {
        return new WebhookView(w.getId(), w.getUrl(), List.copyOf(w.eventSet()), w.isActive(),
                w.getConsecutiveFailures(), w.getDisabledReason(), w.getCreatedAt(), includeSecret ? w.getSecret() : null);
    }
}
