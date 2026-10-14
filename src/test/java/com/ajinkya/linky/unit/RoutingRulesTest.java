package com.ajinkya.linky.unit;

import com.ajinkya.linky.dto.RoutingRule;
import com.ajinkya.linky.util.RoutingRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingRulesTest {

    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_PHONE = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final String ANDROID_TABLET = "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 Chrome/120 Safari/537.36";
    private static final String IPAD = "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
    private static final String WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";
    private static final String MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15";

    @Test
    void classifiesDevicesAndSystems() {
        assertEquals("mobile", RoutingRules.device(IPHONE));
        assertEquals("mobile", RoutingRules.device(ANDROID_PHONE));
        assertEquals("tablet", RoutingRules.device(ANDROID_TABLET));
        assertEquals("tablet", RoutingRules.device(IPAD));
        assertEquals("desktop", RoutingRules.device(WINDOWS));
        assertEquals("desktop", RoutingRules.device(null));

        assertEquals("ios", RoutingRules.os(IPHONE));
        assertEquals("android", RoutingRules.os(ANDROID_PHONE));
        assertEquals("windows", RoutingRules.os(WINDOWS));
        assertEquals("macos", RoutingRules.os(MAC));
    }

    @Test
    void firstMatchingRuleWins_elseDefault() {
        List<RoutingRule> rules = List.of(
                new RoutingRule("os", "ios", "https://apps.apple.com/app"),
                new RoutingRule("device", "mobile", "https://m.example.com"),
                new RoutingRule("country", "DE", "https://example.de"));

        assertEquals("https://apps.apple.com/app", RoutingRules.resolve(rules, "https://example.com", IPHONE, null));
        assertEquals("https://m.example.com", RoutingRules.resolve(rules, "https://example.com", ANDROID_PHONE, null));
        assertEquals("https://example.de", RoutingRules.resolve(rules, "https://example.com", WINDOWS, "de"));
        assertEquals("https://example.com", RoutingRules.resolve(rules, "https://example.com", WINDOWS, "US"));
        assertEquals("https://example.com", RoutingRules.resolve(null, "https://example.com", IPHONE, "DE"));
    }

    @Test
    void validateNormalisesAndRejectsBadInput() {
        List<RoutingRule> ok = RoutingRules.validate(List.of(
                new RoutingRule("DEVICE", "Mobile", "https://m.example.com"),
                new RoutingRule("country", "de", "https://example.de")));
        assertEquals("device", ok.get(0).type());
        assertEquals("mobile", ok.get(0).value());
        assertEquals("DE", ok.get(1).value());

        assertThrows(IllegalArgumentException.class, () -> RoutingRules.validate(List.of(new RoutingRule("device", "watch", "https://x.com"))));
        assertThrows(IllegalArgumentException.class, () -> RoutingRules.validate(List.of(new RoutingRule("country", "DEU", "https://x.com"))));
        assertThrows(IllegalArgumentException.class, () -> RoutingRules.validate(List.of(new RoutingRule("os", "ios", "javascript:alert(1)"))));
        assertThrows(IllegalArgumentException.class, () -> RoutingRules.validate(List.of(new RoutingRule("browser", "chrome", "https://x.com"))));
        assertThrows(IllegalArgumentException.class, () -> RoutingRules.validate(java.util.Collections.nCopies(11, new RoutingRule("os", "ios", "https://x.com"))));
    }

    @Test
    void jsonRoundTrip_andCorruptJsonDegradesGracefully() {
        List<RoutingRule> rules = List.of(new RoutingRule("os", "ios", "https://x.com"));
        assertEquals(rules, RoutingRules.parse(RoutingRules.toJson(rules)));
        assertNull(RoutingRules.parse("{not json"));
        assertNull(RoutingRules.parse(null));
        assertNull(RoutingRules.toJson(List.of()));
    }
}
