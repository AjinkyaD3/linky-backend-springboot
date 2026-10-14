package com.ajinkya.linky.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With a grace period, a just-rotated token is tolerated (two tabs refreshing together) instead of revoking the session. */
@TestPropertySource(properties = "app.auth.refresh-reuse-grace-seconds=30")
public class RefreshGraceTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper mapper;

    private String loginToken() throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"testuser@example.com\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
    }

    private String refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aRotatedTokenIsAcceptedOnceMoreWithinTheGracePeriod_andStillRotates() throws Exception {
        String first = loginToken();
        String second = mapper.readTree(refresh(first)).get("refreshToken").asText();

        // the other tab still holds `first`: allowed inside the grace window
        String third = mapper.readTree(refresh(first)).get("refreshToken").asText();
        assertNotEquals(second, third);

        // and the session continues with the newest token
        refresh(third);
    }
}
