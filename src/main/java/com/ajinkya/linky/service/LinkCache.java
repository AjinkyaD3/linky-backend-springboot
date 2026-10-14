package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.CachedLink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Two-level, cache-aside store for redirect metadata.
 *
 * <ul>
 *   <li>L1: in-process Caffeine, very short TTL. Absorbs hot links without any network hop and
 *       gives single-flight loading (concurrent misses for one code share one load).</li>
 *   <li>L2: Redis, shared by every instance. Also holds negative entries so a flood of unknown
 *       codes cannot be turned into a flood of database queries.</li>
 *   <li>Source of truth: Postgres, supplied by the caller as a loader.</li>
 * </ul>
 *
 * Mutations call {@link #invalidate}, which clears both levels on this instance. Other instances
 * see the change when their L1 entry expires (seconds) - an accepted, documented trade-off.
 * Redis failures degrade to the database instead of failing the redirect.
 */
@Service
public class LinkCache {

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);

    private static final Duration L2_TTL = Duration.ofMinutes(10);
    private static final Duration L2_NEGATIVE_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper;
    private final String prefix;
    private final Cache<String, CachedLink> l1 = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    private final Counter l1Hit;
    private final Counter l2Hit;
    private final Counter dbLoad;

    public LinkCache(RedisTemplate<String, String> redisTemplate, ObjectMapper mapper, MeterRegistry registry,
                     @Value("${app.redis-key-prefix:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.prefix = keyPrefix + "link:";
        this.l1Hit = registry.counter("linky.cache.lookups", "layer", "l1");
        this.l2Hit = registry.counter("linky.cache.lookups", "layer", "l2");
        this.dbLoad = registry.counter("linky.cache.lookups", "layer", "db");
    }

    /** Resolves a code through L1 -> L2 -> {@code dbLoader}. Never returns null. */
    public CachedLink get(String code, Supplier<CachedLink> dbLoader) {
        CachedLink hit = l1.getIfPresent(code);
        if (hit != null) {
            l1Hit.increment();
            return hit;
        }
        return l1.get(code, key -> {
            CachedLink fromRedis = readL2(key);
            if (fromRedis != null) {
                l2Hit.increment();
                return fromRedis;
            }
            dbLoad.increment();
            CachedLink loaded = dbLoader.get();
            writeL2(key, loaded);
            return loaded;
        });
    }

    /** Drop a code from both levels; call after any change that affects redirect behaviour. */
    public void invalidate(String code) {
        l1.invalidate(code);
        try {
            redisTemplate.delete(prefix + code);
        } catch (Exception e) {
            log.warn("Redis invalidate failed for {}: {}", code, e.toString());
        }
    }

    private CachedLink readL2(String code) {
        try {
            String json = redisTemplate.opsForValue().get(prefix + code);
            return json == null ? null : mapper.readValue(json, CachedLink.class);
        } catch (Exception e) {
            log.warn("Redis read failed for {}, falling back to database: {}", code, e.toString());
            return null;
        }
    }

    private void writeL2(String code, CachedLink link) {
        try {
            Duration ttl = link.missing() ? L2_NEGATIVE_TTL : L2_TTL;
            if (link.expiresAtEpochSecond() != null) {
                long untilExpiry = link.expiresAtEpochSecond() - Instant.now().getEpochSecond();
                ttl = Duration.ofSeconds(Math.max(1, Math.min(ttl.toSeconds(), untilExpiry)));
            }
            redisTemplate.opsForValue().set(prefix + code, mapper.writeValueAsString(link), ttl);
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", code, e.toString());
        }
    }
}
