package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.AnalyticsResponse;
import com.ajinkya.linky.dto.ClickOverTime;
import com.ajinkya.linky.dto.TopItem;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.UrlClick;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.UrlClickRepository;
import com.ajinkya.linky.repository.UrlRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public AnalyticsService(UrlRepository urlRepository, UrlClickRepository urlClickRepository) {
        this.urlRepository = urlRepository;
        this.urlClickRepository = urlClickRepository;
    }

    public AnalyticsResponse getAnalytics(String shortCode, int days, com.ajinkya.linky.entity.User user) {
        Url url = urlRepository.findByShortCodeAndUser(shortCode, user)
                .orElseThrow(() -> new ResourceNotFoundException("Short code not found"));

        long totalClicks = urlClickRepository.countByUrl(url);

        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<UrlClick> clicks = urlClickRepository.findByUrlAndClickedAtBetween(url, start, LocalDateTime.now());

        Map<LocalDate, Long> clicksByDate = clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickedAt().toLocalDate(),
                        Collectors.counting()
                ));

        List<ClickOverTime> clicksOverTime = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            clicksOverTime.add(ClickOverTime.builder()
                    .date(date)
                    .count(clicksByDate.getOrDefault(date, 0L))
                    .build());
        }

        List<TopItem> topBrowsers = mapToTopItems(urlClickRepository.findTopBrowsersByUrl(url));
        List<TopItem> topDevices = mapToTopItems(urlClickRepository.findTopDevicesByUrl(url));
        List<TopItem> topCountries = mapToTopItems(urlClickRepository.findTopCountriesByUrl(url));
        List<TopItem> topReferrers = mapToTopItems(urlClickRepository.findTopReferrersByUrl(url));

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .totalClicks(totalClicks)
                .clicksOverTime(clicksOverTime)
                .topBrowsers(topBrowsers)
                .topDevices(topDevices)
                .topCountries(topCountries)
                .topReferrers(topReferrers)
                .build();
    }

    private List<TopItem> mapToTopItems(List<Object[]> results) {
        return results.stream()
                .limit(10)
                .map(row -> TopItem.builder()
                        .name(row[0] != null ? row[0].toString() : "Unknown")
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
