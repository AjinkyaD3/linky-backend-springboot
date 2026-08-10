package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.Visibility;
import com.ajinkya.linky.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AnalyticsControllerTest extends BaseIntegrationTest {

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void getAnalytics_PublicUrl_ReturnsAnalytics() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("pub1");
        url.setVisibility(Visibility.PUBLIC);
        urlRepository.save(url);

        mockMvc.perform(get("/api/analytics/pub1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").exists());
    }

    @Test
    void getAnalytics_PrivateUrl_NotOwner_ReturnsForbidden() throws Exception {
        // 3. Analytics IDOR regression test
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("priv1");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser); // Owned by testUser
        urlRepository.save(url);

        // Anonymous request
        mockMvc.perform(get("/api/analytics/priv1"))
                .andExpect(status().isNotFound()); // Filter returns 404
                
        // Other user request
        mockMvc.perform(get("/api/analytics/priv1")
                .header("Authorization", adminToken)) // Admin is not owner
                .andExpect(status().isNotFound());
    }
    
    @Test
    void getAnalytics_PrivateUrl_Owner_ReturnsAnalytics() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("priv2");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser); // Owned by testUser
        urlRepository.save(url);

        // Owner request
        mockMvc.perform(get("/api/analytics/priv2")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").exists());
    }
}
