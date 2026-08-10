package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.AnalyticsResponse;
import com.ajinkya.linky.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ajinkya.linky.entity.User;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analyticsService.getAnalytics(shortCode, days, user));
    }
}
