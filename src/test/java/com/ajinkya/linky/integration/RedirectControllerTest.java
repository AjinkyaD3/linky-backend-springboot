package com.ajinkya.linky.integration;

import com.ajinkya.linky.dto.UnlockRequest;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.Visibility;
import com.ajinkya.linky.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RedirectControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void redirect_PublicUrl_Redirects() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("pub1");
        url.setVisibility(Visibility.PUBLIC);
        urlRepository.save(url);

        mockMvc.perform(get("/pub1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
    }
    
    @Test
    void redirect_PrivateUrl_NoAuth_ReturnsUnauthorized() throws Exception {
        // 4. Private URL authorization issue regression test
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("priv1");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(get("/priv1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authRequired").value(true));
    }
    
    @Test
    void redirect_PrivateUrl_NotOwner_ReturnsUnauthorized() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("priv2");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(get("/priv2")
                .header("Authorization", adminToken)) // Admin is not owner
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authRequired").value(true));
    }
    
    @Test
    void redirect_PrivateUrl_Owner_Redirects() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("priv3");
        url.setVisibility(Visibility.PRIVATE);
        url.setUser(testUser);
        urlRepository.save(url);

        mockMvc.perform(get("/priv3")
                .header("Authorization", userToken)) // Owner
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
    }
    
    @Test
    void redirect_PasswordProtectedUrl_ReturnsUnauthorized() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("pass1");
        url.setPasswordHash(passwordEncoder.encode("secret"));
        urlRepository.save(url);

        mockMvc.perform(get("/pass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.passwordRequired").value(true));
    }
    
    @Test
    void unlockUrl_ValidPassword_ReturnsOriginalUrl() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("pass2");
        url.setPasswordHash(passwordEncoder.encode("secret"));
        urlRepository.save(url);

        UnlockRequest req = new UnlockRequest();
        req.setPassword("secret");

        mockMvc.perform(post("/pass2/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/target"));
    }
    
    @Test
    void unlockUrl_InvalidPassword_ReturnsForbidden() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/target");
        url.setShortCode("pass3");
        url.setPasswordHash(passwordEncoder.encode("secret"));
        urlRepository.save(url);

        UnlockRequest req = new UnlockRequest();
        req.setPassword("wrong");

        mockMvc.perform(post("/pass3/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
