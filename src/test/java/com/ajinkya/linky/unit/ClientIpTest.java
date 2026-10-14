package com.ajinkya.linky.unit;

import com.ajinkya.linky.util.ClientIp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpTest {

    private MockHttpServletRequest request(String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.9");
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void zeroHops_ignoresForwardedHeader() {
        assertEquals("10.0.0.9", ClientIp.resolve(request("1.2.3.4"), 0));
    }

    @Test
    void oneHop_usesLastEntry_soClientSpoofingIsIgnored() {
        // The client sent "6.6.6.6"; our proxy appended the address it actually saw.
        assertEquals("203.0.113.7", ClientIp.resolve(request("6.6.6.6, 203.0.113.7"), 1));
    }

    @Test
    void twoHops_usesSecondFromRight() {
        assertEquals("203.0.113.7", ClientIp.resolve(request("6.6.6.6, 203.0.113.7, 172.16.0.1"), 2));
    }

    @Test
    void headerMissing_fallsBackToSocketAddress() {
        assertEquals("10.0.0.9", ClientIp.resolve(request(null), 1));
    }

    @Test
    void fewerEntriesThanTrustedHops_fallsBackToSocketAddress() {
        assertEquals("10.0.0.9", ClientIp.resolve(request("203.0.113.7"), 2));
    }
}
