package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.Tag;
import com.ajinkya.linky.repository.TagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class TagControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagRepository tagRepository;

    @Test
    void createTag_ValidInput_ReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/tags")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "NewTag"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("NewTag"));
    }

    @Test
    void createTag_EmptyName_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tags")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void createTag_DuplicateName_ReturnsConflict() throws Exception {
        Tag tag = new Tag();
        tag.setName("ExistingTag");
        tag.setUser(testUser);
        tagRepository.save(tag);
        
        mockMvc.perform(post("/api/tags")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "ExistingTag"))))
                .andExpect(status().isConflict());
    }

    @Test
    void getTags_Authenticated_ReturnsUserTags() throws Exception {
        Tag tag = new Tag();
        tag.setName("MyTag");
        tag.setUser(testUser);
        tagRepository.save(tag);
        
        Tag adminTag = new Tag();
        adminTag.setName("AdminTag");
        adminTag.setUser(adminUser);
        tagRepository.save(adminTag);

        mockMvc.perform(get("/api/tags")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MyTag"));
    }

    @Test
    void deleteTag_Owner_DeletesTag() throws Exception {
        Tag tag = new Tag();
        tag.setName("DeleteMe");
        tag.setUser(testUser);
        tag = tagRepository.save(tag);

        mockMvc.perform(delete("/api/tags/" + tag.getId())
                .header("Authorization", userToken))
                .andExpect(status().isNoContent());
                
        assertFalse(tagRepository.findById(tag.getId()).isPresent());
    }
    
    @Test
    void deleteTag_NotOwner_ReturnsNotFound() throws Exception {
        Tag tag = new Tag();
        tag.setName("KeepMe");
        tag.setUser(testUser);
        tag = tagRepository.save(tag);

        mockMvc.perform(delete("/api/tags/" + tag.getId())
                .header("Authorization", adminToken)) // Not owner
                .andExpect(status().isNotFound()); // Tag controller uses findByIdAndUser which returns empty
                
        assertTrue(tagRepository.findById(tag.getId()).isPresent());
    }
}
