package com.ajinkya.linky.unit;

import com.ajinkya.linky.dto.CachedLink;
import com.ajinkya.linky.service.LinkCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class LinkCacheTest {

    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> ops;
    private LinkCache cache;

    private static CachedLink link(long id) {
        return new CachedLink(id, "https://example.com/" + id, true, false, false, false, null, null, false);
    }

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        cache = new LinkCache(redis, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void secondLookupIsServedFromMemory_notDatabase() {
        AtomicInteger dbCalls = new AtomicInteger();
        cache.get("abc", () -> { dbCalls.incrementAndGet(); return link(1); });
        CachedLink second = cache.get("abc", () -> { dbCalls.incrementAndGet(); return link(1); });

        assertEquals(1, dbCalls.get());
        assertEquals(1L, second.id());
    }

    @Test
    void missingCodesAreNegativeCached() {
        AtomicInteger dbCalls = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            CachedLink result = cache.get("nope", () -> { dbCalls.incrementAndGet(); return CachedLink.notFound(); });
            assertTrue(result.missing());
        }
        assertEquals(1, dbCalls.get());
    }

    @Test
    void redisFailureFallsBackToDatabase() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), any(Duration.class));

        CachedLink result = cache.get("abc", () -> link(7));

        assertEquals(7L, result.id());
    }

    @Test
    void invalidateForcesReload() {
        AtomicInteger dbCalls = new AtomicInteger();
        cache.get("abc", () -> { dbCalls.incrementAndGet(); return link(1); });
        cache.invalidate("abc");
        cache.get("abc", () -> { dbCalls.incrementAndGet(); return link(1); });

        assertEquals(2, dbCalls.get());
        verify(redis).delete(eq("link:abc"));
    }

    @Test
    void concurrentMissesShareOneDatabaseLoad() throws Exception {
        int threads = 16;
        AtomicInteger dbCalls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                return cache.get("hot", () -> {
                    dbCalls.incrementAndGet();
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                    return link(1);
                });
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, dbCalls.get(), "single-flight: a cache stampede must cause exactly one DB load");
    }

    @Test
    void expiredLinkIsDetected() {
        CachedLink past = new CachedLink(1L, "https://e.com", true, false, false, false, null,
                java.time.Instant.now().getEpochSecond() - 5, false);
        assertTrue(past.isExpired());
        assertFalse(link(1).isExpired());
    }
}
