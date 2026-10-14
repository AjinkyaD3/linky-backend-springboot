package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.service.LinkCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Expiry, editable destination, routing rules, bulk create and QR options, end to end through MockMvc. */
public class LinkFeaturesTest extends BaseIntegrationTest {

    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
    private static final String WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    @Autowired private ObjectMapper mapper;
    @Autowired private UrlRepository urlRepository;
    @Autowired private LinkCache linkCache;

    private JsonNode create(String json) throws Exception {
        String body = mockMvc.perform(post("/api/url").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    // ---- expiry

    @Test
    void expiry_isReturned_andPastExpiryRejected() throws Exception {
        String future = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        JsonNode link = create("{\"originalUrl\":\"https://example.com/e\",\"expiresAt\":\"" + future + "\"}");
        assertEquals(future, link.get("expiresAt").asText());

        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/e\",\"expiresAt\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredLink_redirectsToExpiredPage() throws Exception {
        JsonNode link = create("{\"originalUrl\":\"https://example.com/soon\"}");
        String code = link.get("shortCode").asText();
        Url url = urlRepository.findByShortCode(code).orElseThrow();
        url.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        urlRepository.save(url);
        linkCache.invalidate(code);

        mockMvc.perform(get("/" + code)).andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("reason=expired")));
    }

    // ---- edit destination

    @Test
    void editDestination_changesRedirectImmediately_andKeepsCode() throws Exception {
        JsonNode link = create("{\"originalUrl\":\"https://example.com/before\"}");
        String code = link.get("shortCode").asText();

        mockMvc.perform(get("/" + code)).andExpect(header().string("Location", "https://example.com/before"));

        mockMvc.perform(put("/api/url/" + code).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/after\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(code))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/after"));

        // the cached copy must have been invalidated
        mockMvc.perform(get("/" + code)).andExpect(header().string("Location", "https://example.com/after"));
    }

    @Test
    void editDestination_rejectsBadScheme_andOtherUsersLinks() throws Exception {
        String code = create("{\"originalUrl\":\"https://example.com/x\"}").get("shortCode").asText();

        mockMvc.perform(put("/api/url/" + code).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/url/" + code).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://evil.example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void partialUpdate_doesNotWipeTitle() throws Exception {
        String code = create("{\"originalUrl\":\"https://example.com/t\"}").get("shortCode").asText();
        mockMvc.perform(put("/api/url/" + code).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"My title\"}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/url/" + code).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/t2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("My title"));
    }

    // ---- routing

    @Test
    void routingRules_sendVisitorsByDevice() throws Exception {
        JsonNode link = create("{\"originalUrl\":\"https://example.com/web\",\"routingRules\":["
                + "{\"type\":\"os\",\"value\":\"ios\",\"url\":\"https://apps.example.com/ios\"}]}");
        String code = link.get("shortCode").asText();
        assertEquals("ios", link.get("routingRules").get(0).get("value").asText());

        mockMvc.perform(get("/" + code).header("User-Agent", IPHONE))
                .andExpect(header().string("Location", "https://apps.example.com/ios"));
        mockMvc.perform(get("/" + code).header("User-Agent", WINDOWS))
                .andExpect(header().string("Location", "https://example.com/web"));
    }

    @Test
    void routingRules_canBeReplacedAndCleared() throws Exception {
        String code = create("{\"originalUrl\":\"https://example.com/web\",\"routingRules\":["
                + "{\"type\":\"device\",\"value\":\"mobile\",\"url\":\"https://m.example.com\"}]}").get("shortCode").asText();

        mockMvc.perform(put("/api/url/" + code).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"routingRules\":[]}")).andExpect(status().isOk());

        mockMvc.perform(get("/" + code).header("User-Agent", IPHONE))
                .andExpect(header().string("Location", "https://example.com/web"));
    }

    @Test
    void routingRules_invalidRuleRejected() throws Exception {
        mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"routingRules\":["
                                + "{\"type\":\"device\",\"value\":\"toaster\",\"url\":\"https://x.com\"}]}"))
                .andExpect(status().isBadRequest());
    }

    // ---- bulk

    @Test
    void bulkCreate_reportsPerRowResults() throws Exception {
        String body = "{\"links\":[{\"originalUrl\":\"https://example.com/1\"},{\"originalUrl\":\"not a url\"},"
                + "{\"originalUrl\":\"https://example.com/3\",\"isOneTime\":true}]}";
        mockMvc.perform(post("/api/url/bulk").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].link.shortCode").exists())
                .andExpect(jsonPath("$[1].success").value(false))
                .andExpect(jsonPath("$[1].error").exists())
                .andExpect(jsonPath("$[2].link.isOneTime").value(true));
    }

    @Test
    void bulkCreate_requiresAuth_andCapsBatchSize() throws Exception {
        mockMvc.perform(post("/api/url/bulk").contentType(MediaType.APPLICATION_JSON)
                .content("{\"links\":[{\"originalUrl\":\"https://example.com\"}]}")).andExpect(status().isForbidden());

        StringBuilder many = new StringBuilder("{\"links\":[");
        for (int i = 0; i < 101; i++) {
            many.append(i == 0 ? "" : ",").append("{\"originalUrl\":\"https://example.com/").append(i).append("\"}");
        }
        many.append("]}");
        mockMvc.perform(post("/api/url/bulk").header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON).content(many.toString())).andExpect(status().isBadRequest());
    }

    // ---- QR options

    @Test
    void qr_customColoursAndSize() throws Exception {
        String code = create("{\"originalUrl\":\"https://example.com/qr\"}").get("shortCode").asText();

        byte[] png = mockMvc.perform(get("/api/url/" + code + "/qrcode?fg=1a2b3c&bg=ffeeaa&size=300&margin=2"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertEquals(300, image.getWidth());
        int corner = image.getRGB(0, 0) & 0xFFFFFF;
        assertEquals(0xFFEEAA, corner, "background colour should be applied (quiet zone pixel)");

        mockMvc.perform(get("/api/url/" + code + "/qrcode?format=svg&download=true").param("fg", "#ff0000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("fill=\"#ff0000\"")));
    }

    @Test
    void qr_rejectsBadOptions() throws Exception {
        String code = create("{\"originalUrl\":\"https://example.com/qr2\"}").get("shortCode").asText();
        for (String bad : new String[]{"fg=red", "size=5000", "size=10", "margin=99", "fg=000000&bg=000000"}) {
            mockMvc.perform(get("/api/url/" + code + "/qrcode?" + bad)).andExpect(status().isBadRequest());
        }
    }
}
