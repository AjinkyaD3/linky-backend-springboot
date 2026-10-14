package com.ajinkya.linky.service;

import com.ajinkya.linky.service.ClickService.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains the click stream into Postgres.
 *
 * Runs in every instance as a member of one consumer group, so Redis hands each event to exactly
 * one instance and throughput scales by adding instances. Delivery is at-least-once: an entry is
 * acknowledged and deleted only after its batch has committed. If an instance dies mid-batch its
 * entries stay pending and another instance claims them after {@link #CLAIM_IDLE}. An entry that
 * keeps failing is dropped after {@link #MAX_DELIVERIES} attempts so it cannot loop forever.
 */
@Component
@ConditionalOnProperty(name = "app.clicks.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class ClickStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickStreamConsumer.class);

    private static final String GROUP = "click-writers";
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_POLL = 20;
    private static final Duration CLAIM_IDLE = Duration.ofSeconds(60);
    private static final long MAX_DELIVERIES = 5;

    private final RedisTemplate<String, String> redisTemplate;
    private final ClickService clickService;
    private final String streamKey;
    private final String consumerName;
    private final Counter persisted;
    private final Counter failedBatches;
    private final Counter deadLettered;

    public ClickStreamConsumer(RedisTemplate<String, String> redisTemplate,
                               ClickService clickService,
                               ClickEventPublisher publisher,
                               MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.clickService = clickService;
        this.streamKey = publisher.streamKey();
        this.consumerName = hostname() + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.persisted = registry.counter("linky.clicks.persisted");
        this.failedBatches = registry.counter("linky.clicks.batch.failures");
        this.deadLettered = registry.counter("linky.clicks.dead_lettered");
        Gauge.builder("linky.clicks.stream.length", this, ClickStreamConsumer::streamLength)
                .description("Entries currently in the click stream (backlog plus not-yet-trimmed history)")
                .register(registry);
    }

    @PostConstruct
    void ensureGroup() {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                try {
                    connection.streamCommands().xGroupCreate(
                            streamKey.getBytes(StandardCharsets.UTF_8),
                            GROUP, ReadOffset.from("0"), true);
                } catch (Exception alreadyExists) {
                    // BUSYGROUP: the group was created by another instance. Nothing to do.
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Could not initialise click stream group (will retry on first poll): {}", e.toString());
        }
    }

    @Scheduled(fixedDelayString = "${app.clicks.poll-ms:250}")
    public void poll() {
        try {
            for (int i = 0; i < MAX_BATCHES_PER_POLL; i++) {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(BATCH_SIZE),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    return;
                }
                handle(records);
                if (records.size() < BATCH_SIZE) {
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Click stream poll failed: {}", e.toString());
            ensureGroup();
        }
    }

    /** Recovers entries delivered to an instance that crashed or stalled before acknowledging. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reclaimStale() {
        try {
            PendingMessages pending = redisTemplate.opsForStream()
                    .pending(streamKey, GROUP, Range.unbounded(), 200);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            List<RecordId> toClaim = new ArrayList<>();
            List<RecordId> poison = new ArrayList<>();
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(CLAIM_IDLE) < 0) {
                    continue;
                }
                if (message.getTotalDeliveryCount() >= MAX_DELIVERIES) {
                    poison.add(message.getId());
                } else {
                    toClaim.add(message.getId());
                }
            }
            if (!poison.isEmpty()) {
                deadLettered.increment(poison.size());
                log.error("Dropping {} click events after {} failed deliveries", poison.size(), MAX_DELIVERIES);
                ackAndDelete(poison);
            }
            if (!toClaim.isEmpty()) {
                List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                        streamKey, GROUP, consumerName, CLAIM_IDLE,
                        toClaim.toArray(new RecordId[0]));
                if (claimed != null && !claimed.isEmpty()) {
                    log.info("Reclaimed {} stale click events", claimed.size());
                    handle(claimed);
                }
            }
        } catch (Exception e) {
            log.warn("Reclaiming stale click events failed: {}", e.toString());
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        List<ClickEvent> events = new ArrayList<>(records.size());
        List<RecordId> malformed = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            ClickEvent event = toEvent(record.getValue());
            if (event == null) {
                malformed.add(record.getId());
            } else {
                events.add(event);
            }
        }
        if (!malformed.isEmpty()) {
            ackAndDelete(malformed);
        }
        try {
            int inserted = clickService.persistBatch(events);
            persisted.increment(inserted);
            ackAndDelete(records.stream().map(MapRecord::getId).filter(id -> !malformed.contains(id)).toList());
        } catch (Exception e) {
            // Leave the entries pending: they are redelivered by reclaimStale().
            failedBatches.increment();
            log.error("Failed to persist {} click events; will retry: {}", events.size(), e.toString());
        }
    }

    private void ackAndDelete(List<RecordId> ids) {
        if (ids.isEmpty()) {
            return;
        }
        RecordId[] array = ids.toArray(new RecordId[0]);
        redisTemplate.opsForStream().acknowledge(streamKey, GROUP, array);
        // Entries are removed once handled so raw IPs (when geo is enabled) do not linger in Redis.
        redisTemplate.opsForStream().delete(streamKey, array);
    }

    private static ClickEvent toEvent(Map<Object, Object> fields) {
        try {
            return new ClickEvent(
                    Long.valueOf(String.valueOf(fields.get("urlId"))),
                    Long.parseLong(String.valueOf(fields.get("ts"))),
                    asString(fields.get("ua")),
                    asString(fields.get("ref")),
                    asString(fields.get("ipHash")),
                    asString(fields.get("ip")));
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private double streamLength() {
        try {
            Long size = redisTemplate.opsForStream().size(streamKey);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "instance";
        }
    }
}
