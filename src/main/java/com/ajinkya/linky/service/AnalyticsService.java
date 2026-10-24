package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.AnalyticsResponse;
import com.ajinkya.linky.dto.ClickOverTime;
import com.ajinkya.linky.dto.TopItem;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.UrlClickRepository;
import com.ajinkya.linky.repository.UrlRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    /** Longest window the dashboard can ask for; also bounds the size of the per-day series. */
    public static final int MAX_DAYS = 365;
    private static final org.springframework.data.domain.Pageable TOP_N = org.springframework.data.domain.PageRequest.of(0, 10);

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public AnalyticsService(UrlRepository urlRepository, UrlClickRepository urlClickRepository) {
        this.urlRepository = urlRepository;
        this.urlClickRepository = urlClickRepository;
    }

    public AnalyticsResponse getAnalytics(String shortCode, int days, com.ajinkya.linky.entity.User user) {
        Url url = urlRepository.findByShortCodeAndUser(shortCode, user)
                .orElseThrow(() -> new ResourceNotFoundException("Short code not found"));

        days = Math.min(Math.max(days, 1), MAX_DAYS);
        long totalClicks = urlClickRepository.countByUrl(url);

        // window starts at midnight so the first bar of the chart covers a whole day
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<LocalDate, Long> clicksByDate = new HashMap<>();
        for (Object[] row : urlClickRepository.countClicksPerDay(url, start, LocalDateTime.now())) {
            LocalDate date = row[0] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) row[0];
            clicksByDate.put(date, ((Number) row[1]).longValue());
        }

        List<ClickOverTime> clicksOverTime = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            clicksOverTime.add(ClickOverTime.builder()
                    .date(date)
                    .count(clicksByDate.getOrDefault(date, 0L))
                    .build());
        }

        List<TopItem> topBrowsers = mapToTopItems(urlClickRepository.findTopBrowsersByUrl(url, start, TOP_N));
        List<TopItem> topDevices = mapToTopItems(urlClickRepository.findTopDevicesByUrl(url, start, TOP_N));
        List<TopItem> topCountries = mapToTopItems(urlClickRepository.findTopCountriesByUrl(url, start, TOP_N));
        List<TopItem> topCities = mapToTopItems(urlClickRepository.findTopCitiesByUrl(url, start, TOP_N));
        List<TopItem> topReferrers = mapToTopItems(urlClickRepository.findTopReferrersByUrl(url, start, TOP_N));

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .totalClicks(totalClicks)
                .clicksOverTime(clicksOverTime)
                .topBrowsers(topBrowsers)
                .topDevices(topDevices)
                .topCountries(topCountries)
                .topCities(topCities)
                .topReferrers(topReferrers)
                .build();
    }

    private List<TopItem> mapToTopItems(List<Object[]> results) {
        return results.stream()
                .map(row -> TopItem.builder()
                        .name(row[0] != null ? row[0].toString() : "Unknown")
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
