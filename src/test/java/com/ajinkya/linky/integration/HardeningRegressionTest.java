package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.Visibility;
import com.ajinkya.linky.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Token types, the unlock flow's checks, error mapping, analytics bounds and email case handling. */
public class HardeningRegressionTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper mapper;
    @Autowired private UrlRepository urlRepository;

    private static final String UNLOCK_BODY = "{\"password\":\"secret\"}";

    private String loginRefreshToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"testuser@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("refreshToken").asText();
    }

    private Url passwordLink(String code) {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode(code);
        url.setPasswordHash(passwordEncoder.encode("secret"));
        return url;
    }

    // --- token types ---

    @Test
    void refreshToken_CannotBeUsedAsAccessToken() throws Exception {
        String refresh = loginRefreshToken();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_CannotBeUsedAsAccessCookie() throws Exception {
        String refresh = loginRefreshToken();

        mockMvc.perform(get("/api/users/me").cookie(new jakarta.servlet.http.Cookie("linky_at", refresh)))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessToken_CannotBeUsedToRefresh() throws Exception {
        String access = userToken.substring("Bearer ".length());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + access + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- unlock ---

    @Test
    void unlock_ExpiredOneTimeLink_IsRejected() throws Exception {
        Url url = passwordLink("exp1");
        url.setIsOneTime(true);
        url.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        urlRepository.save(url);

        mockMvc.perform(post("/exp1/unlock").contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY))
                .andExpect(status().isNotFound());
        assertTrue(urlRepository.findByShortCode("exp1").orElseThrow().getIsActive(),
                "an expired link must not be consumed");
    }

    @Test
    void unlock_InactiveLink_IsRejected() throws Exception {
        Url url = passwordLink("off1");
        url.setIsActive(false);
        urlRepository.save(url);

        mockMvc.perform(post("/off1/unlock").contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlock_PrivateLink_RequiresOwner() throws Exception {
        Url url = passwordLink("privpw");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(post("/privpw/unlock").contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/privpw/unlock").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/privpw/unlock").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/target"));
    }

    @Test
    void unlock_AppliesRoutingRules() throws Exception {
        Url url = passwordLink("route1");
        url.setRoutingRules("[{\"type\":\"device\",\"value\":\"mobile\",\"url\":\"https://m.example.com\"}]");
        urlRepository.save(url);

        mockMvc.perform(post("/route1/unlock").contentType(MediaType.APPLICATION_JSON).content(UNLOCK_BODY)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://m.example.com"));
    }

    // --- error mapping ---

    @Test
    void malformedJson_Returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void wrongMethod_Returns405() throws Exception {
        mockMvc.perform(delete("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    // --- analytics bounds ---

    @Test
    void analytics_DaysIsClamped() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/a");
        url.setShortCode("days1");
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(get("/api/analytics/days1?days=100000000").header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicksOverTime.length()").value(365));
        mockMvc.perform(get("/api/analytics/days1?days=-5").header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicksOverTime.length()").value(1));
    }

    // --- email case ---

    @Test
    void email_IsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Case\",\"email\":\"Mixed.Case@Example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        assertTrue(userRepository.findByEmail("mixed.case@example.com").isPresent(), "stored lower-case");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dup\",\"email\":\"MIXED.CASE@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"MIXED.case@EXAMPLE.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}
