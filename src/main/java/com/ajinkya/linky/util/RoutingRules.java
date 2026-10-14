package com.ajinkya.linky.util;

import com.ajinkya.linky.dto.RoutingRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validation, (de)serialisation and evaluation of conditional-redirect rules. */
public final class RoutingRules {

    public static final int MAX_RULES = 10;
    private static final Set<String> DEVICES = Set.of("mobile", "tablet", "desktop");
    private static final Set<String> SYSTEMS = Set.of("ios", "android", "windows", "macos", "linux");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoutingRules() {}

    /** Validates and normalises (lower-case device/os, upper-case country). Throws IllegalArgumentException. */
    public static List<RoutingRule> validate(List<RoutingRule> rules) {
        if (rules == null) {
            return List.of();
        }
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("At most " + MAX_RULES + " routing rules are allowed");
        }
        List<RoutingRule> clean = new ArrayList<>();
        for (RoutingRule rule : rules) {
            if (rule == null || rule.type() == null || rule.value() == null || rule.url() == null) {
                throw new IllegalArgumentException("Each routing rule needs a type, value and url");
            }
            String type = rule.type().trim().toLowerCase(Locale.ROOT);
            String value = rule.value().trim();
            switch (type) {
                case "device" -> {
                    value = value.toLowerCase(Locale.ROOT);
                    require(DEVICES.contains(value), "device must be one of " + DEVICES);
                }
                case "os" -> {
                    value = value.toLowerCase(Locale.ROOT);
                    require(SYSTEMS.contains(value), "os must be one of " + SYSTEMS);
                }
                case "country" -> {
                    value = value.toUpperCase(Locale.ROOT);
                    require(value.matches("[A-Z]{2}"), "country must be a 2-letter ISO code");
                }
                default -> throw new IllegalArgumentException("Routing rule type must be device, os or country");
            }
            String url = rule.url().trim();
            require(url.length() <= 2048 && url.matches("(?i)^https?://\\S+$"),
                    "Routing rule url must be an absolute http(s) URL");
            clean.add(new RoutingRule(type, value, url));
        }
        return clean;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    public static String toJson(List<RoutingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(rules);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise routing rules", e);
        }
    }

    public static List<RoutingRule> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<RoutingRule> rules = MAPPER.readValue(json, new TypeReference<List<RoutingRule>>() {});
            return rules.isEmpty() ? null : rules;
        } catch (Exception e) {
            return null; // a corrupt value must never break redirects; fall back to the default target
        }
    }

    /** Picks the destination for this visitor: first matching rule, else {@code defaultUrl}. */
    public static String resolve(List<RoutingRule> rules, String defaultUrl, String userAgent, String country) {
        if (rules == null || rules.isEmpty()) {
            return defaultUrl;
        }
        String device = device(userAgent);
        String os = os(userAgent);
        for (RoutingRule rule : rules) {
            boolean match = switch (rule.type()) {
                case "device" -> rule.value().equals(device);
                case "os" -> rule.value().equals(os);
                case "country" -> country != null && rule.value().equalsIgnoreCase(country.trim());
                default -> false;
            };
            if (match) {
                return rule.url();
            }
        }
        return defaultUrl;
    }

    /** Coarse device class from the User-Agent. (iPadOS 13+ reports a desktop UA, so it reads as desktop.) */
    public static String device(String userAgent) {
        if (userAgent == null) {
            return "desktop";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet") || (ua.contains("android") && !ua.contains("mobile"))) {
            return "tablet";
        }
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("ipod")) {
            return "mobile";
        }
        return "desktop";
    }

    public static String os(String userAgent) {
        if (userAgent == null) {
            return "other";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) return "ios";
        if (ua.contains("android")) return "android";
        if (ua.contains("windows")) return "windows";
        if (ua.contains("mac os x") || ua.contains("macintosh")) return "macos";
        if (ua.contains("linux") || ua.contains("x11")) return "linux";
        return "other";
    }
}
