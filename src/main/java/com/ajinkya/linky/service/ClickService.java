package com.ajinkya.linky.service;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.repository.UrlRepository;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua_parser.Client;
import ua_parser.Parser;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persists a batch of click events: enriches each one (user-agent, optional geo), inserts all rows
 * with one JDBC batch, and bumps each link's counter once per batch instead of once per click.
 */
@Service
public class ClickService {

    /** A click as captured on the request thread (see {@link ClickEventPublisher}). */
    public record ClickEvent(Long urlId, long timestampMillis, String userAgent, String referrer,
                             String ipHash, String ip) {}

    private static final String INSERT_SQL =
            "INSERT INTO url_clicks (url_id, clicked_at, browser, device_type, operating_system, "
                    + "country, city, referrer, ip_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final UrlRepository urlRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Parser uaParser;
    private final DatabaseReader databaseReader;

    @Autowired
    public ClickService(UrlRepository urlRepository,
                        JdbcTemplate jdbcTemplate,
                        Parser uaParser,
                        @Autowired(required = false) DatabaseReader databaseReader) {
        this.urlRepository = urlRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uaParser = uaParser;
        this.databaseReader = databaseReader;
    }

    /**
     * Writes the batch atomically. Events for links that no longer exist are skipped (and count as
     * handled), so one deleted link can never wedge the pipeline.
     *
     * @return number of click rows inserted
     */
    @Transactional
    public int persistBatch(List<ClickEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }
        Set<Long> urlIds = events.stream().map(ClickEvent::urlId).collect(Collectors.toSet());
        Set<Long> existing = urlRepository.findAllById(urlIds).stream()
                .map(Url::getId).collect(Collectors.toSet());

        List<ClickEvent> valid = events.stream().filter(e -> existing.contains(e.urlId())).toList();
        if (valid.isEmpty()) {
            return 0;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, valid, 500, (ps, e) -> {
            String browser = "Unknown";
            String os = "Unknown";
            String device = "Unknown";
            if (e.userAgent() != null) {
                Client client = uaParser.parse(e.userAgent());
                browser = client.userAgent.family;
                os = client.os.family;
                device = client.device.family;
            }
            String[] geo = lookupGeo(e.ip());

            ps.setLong(1, e.urlId());
            ps.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(e.timestampMillis())));
            ps.setString(3, browser);
            ps.setString(4, device);
            ps.setString(5, os);
            ps.setString(6, geo[0]);
            ps.setString(7, geo[1]);
            ps.setString(8, e.referrer());
            ps.setString(9, e.ipHash());
        });

        Map<Long, Long> perUrl = new HashMap<>();
        for (ClickEvent e : valid) {
            perUrl.merge(e.urlId(), 1L, Long::sum);
        }
        perUrl.forEach(urlRepository::incrementClickCountBy);
        return valid.size();
    }

    private String[] lookupGeo(String ip) {
        String country = "Unknown";
        String city = "Unknown";
        if (databaseReader != null && ip != null && !ip.isBlank() && !isLocalhost(ip)) {
            try {
                CityResponse response = databaseReader.city(InetAddress.getByName(ip));
                if (response.getCountry().getName() != null) country = response.getCountry().getName();
                if (response.getCity().getName() != null) city = response.getCity().getName();
            } catch (Exception ignored) {
                // Geo lookup is best-effort.
            }
        }
        return new String[]{country, city};
    }

    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
