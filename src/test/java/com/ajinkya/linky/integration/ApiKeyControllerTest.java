package com.ajinkya.linky.integration;

import com.ajinkya.linky.dto.CreateApiKeyRequest;
import com.ajinkya.linky.entity.ApiKey;
import com.ajinkya.linky.repository.ApiKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApiKeyControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void createApiKey_ValidInput_ReturnsApiKey() throws Exception {
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName("Test Key");

        String response = mockMvc.perform(post("/api/keys")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Key"))
                .andExpect(jsonPath("$.keyValue").exists())
                .andReturn().getResponse().getContentAsString();
                
        // 5. Plaintext API-key storage regression test
        Long keyId = objectMapper.readTree(response).get("id").asLong();
        ApiKey storedKey = apiKeyRepository.findById(keyId).orElseThrow();
        
        // The returned key should be the plain one (starts with lk_), but the DB should have it hashed
        String plainKey = objectMapper.readTree(response).get("keyValue").asText();
        assertTrue(plainKey.startsWith("lk_"));
        
        String storedKeyValue = storedKey.getKeyValue();
        assertNotEquals(plainKey, storedKeyValue);
        assertTrue(passwordEncoder.matches(plainKey, storedKeyValue));
    }

    @Test
    void getApiKeys_ReturnsMaskedKeys() throws Exception {
        // Create one first
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName("Test Key");

        mockMvc.perform(post("/api/keys")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/keys")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Key"))
                .andExpect(jsonPath("$[0].keyValue").value(org.hamcrest.Matchers.containsString("****************")));
    }

    @Test
    void deleteApiKey_Owner_DeletesKey() throws Exception {
        // Create one first
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName("Test Key");

        String response = mockMvc.perform(post("/api/keys")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
                
        Long keyId = objectMapper.readTree(response).get("id").asLong();
        
        mockMvc.perform(delete("/api/keys/" + keyId)
                .header("Authorization", userToken))
                .andExpect(status().isNoContent());
                
        assertTrue(apiKeyRepository.findById(keyId).isEmpty());
    }
    
    @Test
    void deleteApiKey_NotOwner_ReturnsForbidden() throws Exception {
        // Create one as testUser
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName("Test Key");

        String response = mockMvc.perform(post("/api/keys")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
                
        Long keyId = objectMapper.readTree(response).get("id").asLong();
        
        // Admin tries to delete it
        mockMvc.perform(delete("/api/keys/" + keyId)
                .header("Authorization", adminToken)) // Not owner
                .andExpect(status().isForbidden());
                
        // Still exists
        assertTrue(apiKeyRepository.findById(keyId).isPresent());
    }
}
