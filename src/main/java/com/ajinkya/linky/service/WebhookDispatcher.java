package com.ajinkya.linky.service;

import com.ajinkya.linky.entity.Webhook;
import com.ajinkya.linky.entity.WebhookDelivery;
import com.ajinkya.linky.repository.WebhookDeliveryRepository;
import com.ajinkya.linky.util.WebhookUrls;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Delivers queued webhook events.
 *
 * Safe to run on every instance: deliveries are claimed with {@code FOR UPDATE SKIP LOCKED} and a
 * 60 second lease, so two instances never send the same row, and a delivery claimed by a crashed
 * instance becomes due again when its lease expires. Failed attempts are retried with the backoff
 * from {@code app.webhooks.retry-seconds}; after the last one the delivery is marked FAILED.
 *
 * Each request is signed: {@code X-Linky-Signature: sha256=HMAC(secret, timestamp + "." + body)}.
 */
@Component
@ConditionalOnProperty(name = "app.webhooks.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int BATCH = 20;
    private static final Duration LEASE = Duration.ofSeconds(60);

    private static final String CLAIM_SQL =
            "UPDATE webhook_deliveries SET attempts = attempts + 1, next_attempt_at = ? "
                    + "WHERE id IN (SELECT id FROM webhook_deliveries WHERE status = 'PENDING' AND next_attempt_at <= ? "
                    + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING id";

    private final JdbcTemplate jdbc;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookRecorder recorder;
    private final boolean allowInsecure;
    private final long[] retrySeconds;
    private final HttpClient http;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Counter delivered;
    private final Counter retried;
    private final Counter failed;

    public WebhookDispatcher(JdbcTemplate jdbc, WebhookDeliveryRepository deliveries, WebhookRecorder recorder,
                             MeterRegistry registry,
                             @Value("${app.webhooks.allow-insecure:false}") boolean allowInsecure,
                             @Value("${app.webhooks.retry-seconds:10,60,300,1800,7200}") String retryList) {
        this.jdbc = jdbc;
        this.deliveries = deliveries;
        this.recorder = recorder;
        this.allowInsecure = allowInsecure;
        this.retrySeconds = java.util.Arrays.stream(retryList.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .mapToLong(Long::parseLong).toArray();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER) // a redirect could bounce us to an internal address
                .build();
        this.delivered = registry.counter("linky.webhooks.delivered");
        this.retried = registry.counter("linky.webhooks.retried");
        this.failed = registry.counter("linky.webhooks.failed");
    }

    @Scheduled(fixedDelayString = "${app.webhooks.poll-ms:2000}")
    public void tick() {
        List<Long> ids;
        try {
            LocalDateTime now = LocalDateTime.now();
            ids = jdbc.queryForList(CLAIM_SQL, Long.class,
                    Timestamp.valueOf(now.plus(LEASE)), Timestamp.valueOf(now), BATCH);
        } catch (Exception e) {
            log.warn("Webhook claim failed: {}", e.toString());
            return;
        }
        if (ids.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(ids.stream()
                .map(id -> CompletableFuture.runAsync(() -> deliver(id), pool))
                .toArray(CompletableFuture[]::new)).join();
    }

    private void deliver(Long id) {
        try {
            WebhookDelivery delivery = deliveries.findWithWebhook(id).orElse(null);
            if (delivery == null) {
                return;
            }
            Webhook webhook = delivery.getWebhook();
            if (!webhook.isActive() && !WebhookService.TEST.equals(delivery.getEvent())) {
                recorder.abandon(id, "Webhook is disabled");
                return;
            }
            URI uri = WebhookUrls.validate(webhook.getUrl(), allowInsecure);

            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Linky-Webhooks/1.0")
                    .header("X-Linky-Event", delivery.getEvent())
                    .header("X-Linky-Delivery", String.valueOf(delivery.getId()))
                    .header("X-Linky-Timestamp", timestamp)
                    .header("X-Linky-Signature", "sha256=" + sign(webhook.getSecret(), timestamp, delivery.getPayload()))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .build();

            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                recorder.success(id, status);
                delivered.increment();
            } else {
                fail(delivery, status, "Endpoint responded with HTTP " + status);
            }
        } catch (IllegalArgumentException e) {
            // the stored URL no longer passes the safety check (e.g. its DNS now points inside our network)
            recorder.abandon(id, "Blocked: " + e.getMessage());
            failed.increment();
        } catch (Exception e) {
            deliveries.findWithWebhook(id).ifPresent(d -> fail(d, null, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private void fail(WebhookDelivery delivery, Integer status, String error) {
        // attempts was incremented when the row was claimed, so it is the number of tries made so far
        int attempt = delivery.getAttempts();
        boolean last = attempt > retrySeconds.length;
        LocalDateTime next = last ? null : LocalDateTime.now().plusSeconds(retrySeconds[attempt - 1]);
        recorder.failure(delivery.getId(), status, error, last, next);
        (last ? failed : retried).increment();
    }

    static String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign webhook payload", e);
        }
    }

    /** Housekeeping: finished deliveries are only useful for debugging, so keep a week of them. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldDeliveries() {
        try {
            int removed = purge();
            if (removed > 0) {
                log.info("Purged {} old webhook deliveries", removed);
            }
        } catch (Exception e) {
            log.warn("Webhook purge failed: {}", e.toString());
        }
    }

    private int purge() {
        return jdbc.update("DELETE FROM webhook_deliveries WHERE status <> 'PENDING' AND created_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(7)));
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
