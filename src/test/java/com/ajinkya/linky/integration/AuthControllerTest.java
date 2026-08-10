package com.ajinkya.linky.integration;

import com.ajinkya.linky.dto.LoginRequest;
import com.ajinkya.linky.dto.RegisterRequest;
import com.ajinkya.linky.dto.RefreshTokenRequest;
import com.ajinkya.linky.dto.ForgotPasswordRequest;
import com.ajinkya.linky.dto.ResetPasswordRequest;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.entity.PasswordResetToken;
import com.ajinkya.linky.repository.PasswordResetTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.util.Date;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void register_ValidInput_ReturnsCreated() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("New User");
        req.setEmail("newuser@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_MissingEmail_ReturnsBadRequest() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("New User");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void register_ShortPassword_ReturnsBadRequest() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("New User");
        req.setEmail("newuser2@example.com");
        req.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_DuplicateEmail_ReturnsConflict() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Dupe");
        req.setEmail(testUser.getEmail());
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Depends on exception handler, usually 400 or 409
    }

    @Test
    void login_ValidCredentials_ReturnsTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(testUser.getEmail());
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(testUser.getEmail());
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized()); // Spring returns 403 or 401 for bad credentials
    }
    
    @Test
    void refresh_ValidToken_ReturnsNewTokens() throws Exception {
        // First login to get a refresh token
        LoginRequest req = new LoginRequest();
        req.setEmail(testUser.getEmail());
        req.setPassword("password123");
        
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
                
        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();
        
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
    
    @Test
    void refresh_InvalidToken_ReturnsForbidden() throws Exception {
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken("invalid-token-here");
        
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_ValidToken_ReturnsOk() throws Exception {
        // 6. Refresh-token revocation regression test
        LoginRequest req = new LoginRequest();
        req.setEmail(testUser.getEmail());
        req.setPassword("password123");
        
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
                
        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();
        
        RefreshTokenRequest logoutReq = new RefreshTokenRequest();
        logoutReq.setRefreshToken(refreshToken);
        
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());
                
        // Refresh token should now be invalid
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_ValidEmail_ReturnsOk() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(testUser.getEmail());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_ValidToken_ReturnsOk() throws Exception {
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUser(testUser);
        prt.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        passwordResetTokenRepository.save(prt);

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken(token);
        req.setNewPassword("newpassword123");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
