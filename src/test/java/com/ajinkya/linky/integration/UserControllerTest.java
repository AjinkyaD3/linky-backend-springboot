package com.ajinkya.linky.integration;

import com.ajinkya.linky.dto.ChangePasswordRequest;
import com.ajinkya.linky.dto.UpdateProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getCurrentUser_Authenticated_ReturnsUser() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    @Test
    void getCurrentUser_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_ValidInput_UpdatesUser() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Updated Name");

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void changePassword_ValidInput_ChangesPassword() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("password123");
        req.setNewPassword("newpassword123");

        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
                
        // Verify we can login with new password
        com.ajinkya.linky.dto.LoginRequest loginReq = new com.ajinkya.linky.dto.LoginRequest();
        loginReq.setEmail(testUser.getEmail());
        loginReq.setPassword("newpassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());
                
        // Restore password for other tests
        testUser.setPassword(passwordEncoder.encode("password123"));
        userRepository.save(testUser);
    }
    
    @Test
    void changePassword_InvalidCurrentPassword_ReturnsBadRequest() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("newpassword123");

        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Or 500 depending on exception handler mapping
    }

    @Test
    void deleteAccount_Authenticated_DeletesUser() throws Exception {
        // Create a temporary user for deletion
        com.ajinkya.linky.entity.User tempUser = new com.ajinkya.linky.entity.User();
        tempUser.setName("Delete Me");
        tempUser.setEmail("delete@example.com");
        tempUser.setPassword("password123");
        tempUser = userRepository.save(tempUser);
        
        String tempToken = "Bearer " + jwtUtil.generateAccessToken(tempUser.getEmail(), tempUser.getRole().name());

        mockMvc.perform(delete("/api/users/me")
                .header("Authorization", tempToken))
                .andExpect(status().isNoContent());
                
        assertFalse(userRepository.findById(tempUser.getId()).isPresent());
    }

    @Test
    void uploadAvatar_Authenticated_ReturnsUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/users/me/avatar")
                .file(file)
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.url").isString());
    }

    @Test
    void uploadAvatar_Unauthenticated_ReturnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/users/me/avatar")
                .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadAvatar_MissingFile_ReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar")
                .header("Authorization", userToken))
                .andExpect(status().isBadRequest());
    }
}
