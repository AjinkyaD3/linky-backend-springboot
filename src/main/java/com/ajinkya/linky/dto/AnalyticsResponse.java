package com.ajinkya.linky.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyticsResponse {
    private String shortCode;
    private long totalClicks;
    private List<ClickOverTime> clicksOverTime;
    private List<TopItem> topBrowsers;
    private List<TopItem> topDevices;
    private List<TopItem> topCountries;
    private List<TopItem> topReferrers;
}
