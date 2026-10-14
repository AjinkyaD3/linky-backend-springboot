package com.ajinkya.linky.service;

import com.ajinkya.linky.util.ClientIp;
import com.maxmind.geoip2.DatabaseReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes click events to a Redis Stream from the request thread.
 *
 * Only cheap work happens here (read headers, hash the IP, one XADD). User-agent parsing, geo
 * lookup and database writes are done by {@link ClickStreamConsumer}, so redirect latency does not
 * depend on the database. Everything is captured eagerly: the servlet request must never be
 * touched from another thread (Tomcat recycles it once the response is committed).
 */
@Service
public class ClickEventPublisher {

    public static final String STREAM_NAME = "linky:clicks";
    private static final long MAX_STREAM_LENGTH = 200_000;
    private static final int MAX_FIELD = 512;

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final boolean geoEnabled;
    private final int trustedProxyHops;
    private final String streamKey;
    private final String ipHashSalt;
    private final Counter published;
    private final Counter dropped;

    public ClickEventPublisher(RedisTemplate<String, String> redisTemplate,
                               ObjectProvider<DatabaseReader> geoReader,
                               MeterRegistry registry,
                               @Value("${app.trusted-proxy-hops:0}") int trustedProxyHops,
                               @Value("${app.ip-hash-salt:}") String ipHashSalt,
                               @Value("${app.redis-key-prefix:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.geoEnabled = geoReader.getIfAvailable() != null;
        this.trustedProxyHops = trustedProxyHops;
        this.ipHashSalt = ipHashSalt;
        this.streamKey = keyPrefix + STREAM_NAME;
        this.published = registry.counter("linky.clicks.published");
        this.dropped = registry.counter("linky.clicks.dropped");
    }

    public String streamKey() {
        return streamKey;
    }

    public void publish(Long urlId, HttpServletRequest request) {
        try {
            String ip = ClientIp.resolve(request, trustedProxyHops);
            Map<String, String> fields = new HashMap<>();
            fields.put("urlId", String.valueOf(urlId));
            fields.put("ts", String.valueOf(System.currentTimeMillis()));
            put(fields, "ua", request.getHeader("User-Agent"));
            put(fields, "ref", request.getHeader("Referer"));
            put(fields, "ipHash", hash(ip));
            // Raw IP only travels through Redis when a geo database is configured to use it.
            if (geoEnabled) {
                put(fields, "ip", ip);
            }
            redisTemplate.opsForStream().add(
                    StreamRecords.string(fields).withStreamKey(streamKey),
                    XAddOptions.maxlen(MAX_STREAM_LENGTH).approximateTrimming(true));
            published.increment();
        } catch (Exception e) {
            // Analytics are best-effort: never fail or slow a redirect because Redis is unhappy.
            dropped.increment();
            log.warn("Dropping click event for url {}: {}", urlId, e.toString());
        }
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value.length() > MAX_FIELD ? value.substring(0, MAX_FIELD) : value);
        }
    }

    private String hash(String ip) {
        if (ip == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((ipHashSalt + ip).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
