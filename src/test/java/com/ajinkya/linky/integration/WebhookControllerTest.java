package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.WebhookDelivery;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.repository.WebhookDeliveryRepository;
import com.ajinkya.linky.service.ClickService;
import com.ajinkya.linky.service.ClickService.ClickEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class WebhookControllerTest extends BaseIntegrationTest {

    private static final String PUBLIC_URL = "https://93.184.216.34/hook"; // IP literal: no DNS in tests

    @Autowired private ObjectMapper mapper;
    @Autowired private WebhookDeliveryRepository deliveries;
    @Autowired private UrlRepository urlRepository;
    @Autowired private ClickService clickService;

    private JsonNode createWebhook(String url, String events) throws Exception {
        String body = mockMvc.perform(post("/api/webhooks").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\",\"events\":" + events + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void create_returnsSecretOnce() throws Exception {
        JsonNode created = createWebhook(PUBLIC_URL, "[\"link.created\"]");
        assertTrue(created.get("secret").asText().startsWith("whsec_"));
        assertTrue(created.get("active").asBoolean());

        mockMvc.perform(get("/api/webhooks").header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value(PUBLIC_URL))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    void rejectsUnsafeUrlsAndBadEvents() throws Exception {
        for (String bad : new String[]{"http://93.184.216.34/x", "https://127.0.0.1/x", "https://169.254.169.254/x",
                "https://10.0.0.1/x", "javascript:alert(1)"}) {
            mockMvc.perform(post("/api/webhooks").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + bad + "\",\"events\":[\"link.created\"]}")).andExpect(status().isBadRequest());
        }
        for (String badEvents : new String[]{"[]", "[\"link.deleted\"]", "[\"webhook.test\"]"}) {
            mockMvc.perform(post("/api/webhooks").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + PUBLIC_URL + "\",\"events\":" + badEvents + "}")).andExpect(status().isBadRequest());
        }
    }

    @Test
    void ownershipAndAuth() throws Exception {
        long id = createWebhook(PUBLIC_URL, "[\"link.created\"]").get("id").asLong();

        mockMvc.perform(get("/api/webhooks")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/webhooks/" + id).header("Authorization", adminToken)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/webhooks/" + id).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/webhooks/" + id + "/test").header("Authorization", adminToken)).andExpect(status().isNotFound());
    }

    @Test
    void creatingALink_queuesDeliveryOnlyForSubscribedActiveWebhooks() throws Exception {
        long subscribed = createWebhook(PUBLIC_URL, "[\"link.created\"]").get("id").asLong();
        createWebhook("https://93.184.216.35/other", "[\"link.milestone\"]");
        long paused = createWebhook("https://93.184.216.36/paused", "[\"link.created\"]").get("id").asLong();
        mockMvc.perform(put("/api/webhooks/" + paused).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://example.com/new\"}")).andExpect(status().isCreated());

        List<WebhookDelivery> all = deliveries.findAll();
        assertEquals(1, all.size(), "only the active, subscribed webhook gets the event");
        WebhookDelivery d = all.get(0);
        assertEquals(subscribed, d.getWebhook().getId());
        assertEquals("link.created", d.getEvent());
        JsonNode payload = mapper.readTree(d.getPayload());
        assertEquals("link.created", payload.get("event").asText());
        assertEquals("https://example.com/new", payload.get("data").get("originalUrl").asText());
        assertTrue(payload.get("data").get("shortUrl").asText().endsWith("/" + payload.get("data").get("shortCode").asText()));
        assertNotNull(payload.get("id"));
    }

    @Test
    void anonymousLinkCreation_publishesNothing() throws Exception {
        createWebhook(PUBLIC_URL, "[\"link.created\"]");
        mockMvc.perform(post("/api/url").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://example.com/anon\"}")).andExpect(status().isCreated());
        assertEquals(0, deliveries.count());
    }

    @Test
    void milestone_firesWhenABatchCrossesAThreshold() throws Exception {
        createWebhook(PUBLIC_URL, "[\"link.milestone\"]");
        String code = mapper.readTree(mockMvc.perform(post("/api/url").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"originalUrl\":\"https://example.com/m\"}"))
                .andReturn().getResponse().getContentAsString()).get("shortCode").asText();
        Url url = urlRepository.findByShortCode(code).orElseThrow();
        url.setClickCount(8L);
        urlRepository.saveAndFlush(url);

        // 3 more clicks take it from 8 to 11, crossing 10
        List<ClickEvent> clicks = List.of(
                new ClickEvent(url.getId(), System.currentTimeMillis(), null, null, null, null),
                new ClickEvent(url.getId(), System.currentTimeMillis(), null, null, null, null),
                new ClickEvent(url.getId(), System.currentTimeMillis(), null, null, null, null));
        assertEquals(3, clickService.persistBatch(clicks));

        List<WebhookDelivery> all = deliveries.findAll();
        assertEquals(1, all.size());
        JsonNode data = mapper.readTree(all.get(0).getPayload()).get("data");
        assertEquals(10, data.get("milestone").asInt());
        assertEquals(11, data.get("totalClicks").asInt());

        // another batch that does not cross a threshold adds nothing
        clickService.persistBatch(List.of(new ClickEvent(url.getId(), System.currentTimeMillis(), null, null, null, null)));
        assertEquals(1, deliveries.count());
    }

    @Test
    void testPing_queuesDelivery_andDeliveriesAreListed() throws Exception {
        long id = createWebhook(PUBLIC_URL, "[\"link.created\"]").get("id").asLong();
        mockMvc.perform(post("/api/webhooks/" + id + "/test").header("Authorization", userToken)).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/webhooks/" + id + "/deliveries").header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event").value("webhook.test"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void reEnablingResetsFailureState_andLimitIsEnforced() throws Exception {
        long id = createWebhook(PUBLIC_URL, "[\"link.created\"]").get("id").asLong();
        mockMvc.perform(put("/api/webhooks/" + id).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/webhooks/" + id + "/test").header("Authorization", userToken)).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/webhooks/" + id).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.consecutiveFailures").value(0));

        for (int i = 0; i < 4; i++) createWebhook("https://93.184.216." + (40 + i) + "/h", "[\"link.created\"]");
        mockMvc.perform(post("/api/webhooks").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://93.184.216.99/h\",\"events\":[\"link.created\"]}")).andExpect(status().isBadRequest());
    }
}
