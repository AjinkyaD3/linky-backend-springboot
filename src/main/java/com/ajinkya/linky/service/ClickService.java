package com.ajinkya.linky.service;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.UrlClick;
import com.ajinkya.linky.repository.UrlClickRepository;
import com.ajinkya.linky.repository.UrlRepository;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua_parser.Client;
import ua_parser.Parser;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class ClickService {

    private final UrlClickRepository urlClickRepository;
    private final UrlRepository urlRepository;
    private final Parser uaParser;
    private final DatabaseReader databaseReader;

    @Autowired
    public ClickService(UrlClickRepository urlClickRepository, 
                        UrlRepository urlRepository, 
                        Parser uaParser, 
                        @Autowired(required = false) DatabaseReader databaseReader) {
        this.urlClickRepository = urlClickRepository;
        this.urlRepository = urlRepository;
        this.uaParser = uaParser;
        this.databaseReader = databaseReader;
    }

    @Async
    @Transactional
    public void logClick(Url url, HttpServletRequest request) {
        try {
            String userAgentString = request.getHeader("User-Agent");
            String referrer = request.getHeader("Referer");
            String ipAddress = extractIpAddress(request);
            String ipHash = hashIp(ipAddress);

            String browser = "Unknown";
            String os = "Unknown";
            String device = "Unknown";

            if (userAgentString != null) {
                Client client = uaParser.parse(userAgentString);
                browser = client.userAgent.family;
                os = client.os.family;
                device = client.device.family;
            }

            String country = "Unknown";
            String city = "Unknown";

            if (databaseReader != null && ipAddress != null && !ipAddress.isBlank() && !isLocalhost(ipAddress)) {
                try {
                    InetAddress ip = InetAddress.getByName(ipAddress);
                    CityResponse response = databaseReader.city(ip);
                    country = response.getCountry().getName();
                    city = response.getCity().getName();
                } catch (Exception e) {
                    // Ignore geoip lookup errors
                }
            }

            UrlClick click = UrlClick.builder()
                    .url(url)
                    .browser(browser)
                    .operatingSystem(os)
                    .deviceType(device)
                    .referrer(referrer)
                    .ipHash(ipHash)
                    .country(country)
                    .city(city)
                    .build();

            urlClickRepository.save(click);

            // Use atomic update to prevent race conditions
            urlRepository.incrementClickCount(url.getId());

        } catch (Exception e) {
            // Log error but don't fail the async task completely
            e.printStackTrace();
        }
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String hashIp(String ip) {
        if (ip == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
    
    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
