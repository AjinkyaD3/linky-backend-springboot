package com.ajinkya.linky.integration;

import com.ajinkya.linky.dto.CreateUrlRequest;
import com.ajinkya.linky.dto.UpdateUrlRequest;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.Visibility;
import com.ajinkya.linky.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class UrlControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void createUrl_Anonymous_ReturnsCreated() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");

        mockMvc.perform(post("/api/url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").exists());
    }

    @Test
    void createUrl_Authenticated_LinksToUser() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");

        String response = mockMvc.perform(post("/api/url")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
                
        String shortCode = objectMapper.readTree(response).get("shortCode").asText();
        Url url = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertEquals(testUser.getId(), url.getUser().getId());
    }
    
    @Test
    void createUrl_InvalidUrl_ReturnsBadRequest() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("invalid-url");

        mockMvc.perform(post("/api/url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUrlById_Owner_ReturnsUrl() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1234");
        url.setUser(testUser);
        url = urlRepository.save(url);

        mockMvc.perform(get("/api/url/" + url.getId())
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc1234"));
    }

    @Test
    void getUrlById_NotOwner_ReturnsForbidden() throws Exception {
        // 2. URL IDOR regression test
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1234");
        url.setUser(testUser); // Owned by testUser
        url = urlRepository.save(url);

        mockMvc.perform(get("/api/url/" + url.getId())
                .header("Authorization", adminToken)) // Requested by adminUser
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyUrls_ReturnsOwnedUrls() throws Exception {
        // 1. Global URL data exposure regression test
        Url url1 = new Url();
        url1.setOriginalUrl("https://example.com/1");
        url1.setShortCode("abc1");
        url1.setUser(testUser);
        urlRepository.save(url1);

        Url url2 = new Url();
        url2.setOriginalUrl("https://example.com/2");
        url2.setShortCode("abc2");
        url2.setUser(adminUser); // Someone else's URL
        urlRepository.save(url2);

        mockMvc.perform(get("/api/url/my")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shortCode").value("abc1"));
    }

    @Test
    void updateUrl_Owner_ReturnsUpdated() throws Exception {
        // 8. URL update validation regression test
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        urlRepository.save(url);

        UpdateUrlRequest req = new UpdateUrlRequest();
        req.setTitle("New Title");

        mockMvc.perform(put("/api/url/" + url.getShortCode())
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));
    }

    @Test
    void updateUrl_NotOwner_ReturnsForbidden() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        urlRepository.save(url);

        UpdateUrlRequest req = new UpdateUrlRequest();
        req.setTitle("New Title");

        mockMvc.perform(put("/api/url/" + url.getShortCode())
                .header("Authorization", adminToken) // Not owner
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUrl_Owner_SoftDeletes() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        url = urlRepository.save(url);

        mockMvc.perform(delete("/api/url/" + url.getId())
                .header("Authorization", userToken))
                .andExpect(status().isNoContent());

        // Verify soft delete
        assertTrue(urlRepository.findById(url.getId()).get().getIsDeleted());
    }

    @Test
    void toggleFavorite_Owner_Toggles() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        url.setIsFavorite(false);
        urlRepository.save(url);

        mockMvc.perform(put("/api/url/" + url.getShortCode() + "/favorite")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(true));
    }
    
    @Test
    void toggleArchive_Owner_Toggles() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        url.setIsArchived(false);
        urlRepository.save(url);

        mockMvc.perform(put("/api/url/" + url.getShortCode() + "/archive")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true));
    }
    
    @Test
    void getMyFavorites_ReturnsFavorites() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        url.setIsFavorite(true);
        urlRepository.save(url);

        mockMvc.perform(get("/api/url/my/favorites")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
    
    @Test
    void getMyArchived_ReturnsArchived() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        url.setUser(testUser);
        url.setIsArchived(true);
        urlRepository.save(url);

        mockMvc.perform(get("/api/url/my/archived")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void duplicateUrl_Owner_CreatesDuplicate() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/long");
        url.setShortCode("abc1");
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(post("/api/url/" + url.getShortCode() + "/duplicate")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/long"))
                .andExpect(jsonPath("$.shortCode").exists())
                .andExpect(jsonPath("$.shortCode").value(org.hamcrest.Matchers.not("abc1")));
    }
    
    @Test
    void getQRCode_Public_ReturnsImage() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc1");
        urlRepository.save(url);

        mockMvc.perform(get("/api/url/abc1/qrcode"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }
}
