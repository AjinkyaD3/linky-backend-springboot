package com.ajinkya.linky.unit;

import com.ajinkya.linky.util.WebhookUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The SSRF guard. IP literals are used so no test depends on DNS or network access. */
class WebhookUrlsTest {

    @Test
    void acceptsPublicHttpsAddresses() {
        assertEquals("https://93.184.216.34/hook", WebhookUrls.validate("https://93.184.216.34/hook", false).toString());
        assertDoesNotThrow(() -> WebhookUrls.validate("https://[2606:2800:220:1:248:1893:25c8:1946]/hook", false));
    }

    @Test
    void rejectsInternalAndSpecialAddresses() {
        String[] blocked = {
                "https://127.0.0.1/x", "https://127.1.2.3/x", "https://[::1]/x", "https://0.0.0.0/x",
                "https://10.0.0.5/x", "https://172.16.4.4/x", "https://192.168.1.1/x",
                "https://169.254.169.254/latest/meta-data",   // cloud metadata service
                "https://100.64.0.1/x",                       // carrier-grade NAT
                "https://[fd00::1]/x", "https://[fe80::1]/x", // IPv6 unique-local, link-local
                "https://224.0.0.1/x",                        // multicast
                "https://255.255.255.255/x"
        };
        for (String url : blocked) {
            assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate(url, false), url);
        }
    }

    @Test
    void requiresHttpsAndHostWithoutCredentials() {
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("http://93.184.216.34/x", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("ftp://93.184.216.34/x", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("javascript:alert(1)", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("https://user:pw@93.184.216.34/x", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("not a url", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("", false));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate(null, false));
    }

    @Test
    void insecureModeAllowsLocalDevelopmentTargets() {
        assertDoesNotThrow(() -> WebhookUrls.validate("http://localhost:9100/hook", true));
        assertDoesNotThrow(() -> WebhookUrls.validate("http://127.0.0.1:9100/hook", true));
        assertThrows(IllegalArgumentException.class, () -> WebhookUrls.validate("ftp://localhost/x", true));
    }
}
