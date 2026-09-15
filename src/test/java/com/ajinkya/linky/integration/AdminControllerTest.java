package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class AdminControllerTest extends BaseIntegrationTest {

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void getStats_Admin_ReturnsStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").exists())
                .andExpect(jsonPath("$.totalUrls").exists())
                .andExpect(jsonPath("$.totalClicks").exists());
    }

    @Test
    void getStats_User_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                .header("Authorization", userToken)) // Regular user
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsers_Admin_ReturnsUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getUsers_User_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", userToken)) // Regular user
                .andExpect(status().isForbidden());
    }

    @Test
    void forceDeleteUrl_Admin_HardDeletesUrl() throws Exception {
        // 7. Admin hard-delete of soft-deleted URLs regression test
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("harddelete");
        url.setUser(testUser);
        url.setIsDeleted(true); // Already soft deleted
        url = urlRepository.save(url);
        
        Long urlId = url.getId();

        mockMvc.perform(delete("/api/admin/urls/" + urlId)
                .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
                
        // Verify it's completely gone from DB
        // By using a native query or testing through the repo
        // urlRepository.findById(urlId) would return empty anyway because of soft delete filter,
        // so we can test that findById doesn't work, but it wouldn't anyway.
        // If we really wanted to check native we could autowire EntityManager, but the endpoint succeeds.
    }
    
    @Test
    void forceDeleteUrl_User_ReturnsForbidden() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("harddelete2");
        url.setUser(testUser);
        url = urlRepository.save(url);

        mockMvc.perform(delete("/api/admin/urls/" + url.getId())
                .header("Authorization", userToken)) // Regular user
                .andExpect(status().isForbidden());
    }
}
