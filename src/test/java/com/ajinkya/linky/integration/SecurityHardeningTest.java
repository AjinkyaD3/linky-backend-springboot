package com.ajinkya.linky.integration;

import com.ajinkya.linky.entity.RefreshToken;
import com.ajinkya.linky.repository.RefreshTokenRepository;
import com.ajinkya.linky.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Alias validation, consistent 404s, hashed and rotated refresh tokens, session revocation, avatar validation. */
public class SecurityHardeningTest extends BaseIntegrationTest {

    // smallest bytes that identify as each image type
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Autowired private ObjectMapper mapper;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private com.ajinkya.linky.repository.PasswordResetTokenRepository resetTokens;

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"testuser@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    // ---------- aliases

    @Test
    void alias_invalidFormatsAreRejectedWithAClearError() throws Exception {
        String[] bad = {"ab", "this-alias-is-way-longer-than-twenty-chars", "has space", "a/b", "../x", "dot.dot", "emoji\\u00e9"};
        for (String alias : bad) {
            mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"" + alias + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.customAlias").value(containsString("Alias")));
        }
    }

    @Test
    void alias_reservedWordsAreRejected_validOnesWork_duplicatesConflict() throws Exception {
        for (String reserved : new String[]{"api", "ACTUATOR", "swagger-ui", "login", "p"}) {
            mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"" + reserved + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        String ok = "{\"originalUrl\":\"https://example.com\",\"customAlias\":\"my-sale_2026\"}";
        mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value("my-sale_2026"));
        mockMvc.perform(post("/api/url").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isConflict());
    }

    // ---------- ownership errors are uniform

    @Test
    void deletingSomeoneElsesLink_looksLikeANotFound() throws Exception {
        String created = mockMvc.perform(post("/api/url").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"originalUrl\":\"https://example.com/own\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/url/" + id).header("Authorization", adminToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        mockMvc.perform(delete("/api/url/99999999").header("Authorization", adminToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/api/url/" + id).header("Authorization", adminToken)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tags/99999999").header("Authorization", userToken)).andExpect(status().isNotFound());
    }

    // ---------- refresh tokens

    @Test
    void refreshTokens_areStoredOnlyAsHashes() throws Exception {
        String raw = login().get("refreshToken").asText();
        assertTrue(refreshTokens.findByToken(raw).isEmpty(), "the raw token must not be in the database");
        assertTrue(refreshTokens.findByToken(Hashing.sha256Hex(raw)).isPresent());
    }

    @Test
    void twoLoginsInTheSameInstant_getDifferentTokens_andBothWork() throws Exception {
        String a = login().get("refreshToken").asText();
        String b = login().get("refreshToken").asText();
        assertNotEquals(a, b, "jti must make every refresh token unique");
        refresh(a).andExpect(status().isOk());
        refresh(b).andExpect(status().isOk());
    }

    @Test
    void rotation_issuesANewToken_andOldTokenReuseRevokesTheWholeSession() throws Exception {
        String first = login().get("refreshToken").asText();
        String second = mapper.readTree(refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        assertNotEquals(first, second);

        // grace period is 0 in tests: replaying the rotated-out token is treated as theft
        refresh(first).andExpect(status().isBadRequest());

        // ...and the session is gone, so even the legitimate newest token no longer works
        refresh(second).andExpect(status().isBadRequest());
        assertTrue(refreshTokens.findByToken(Hashing.sha256Hex(second)).isEmpty());
    }

    @Test
    void changingThePassword_endsEverySession() throws Exception {
        String token = login().get("refreshToken").asText();
        mockMvc.perform(put("/api/users/me/password").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"another-pass-456\"}"))
                .andExpect(status().isOk());

        refresh(token).andExpect(status().isBadRequest());
        assertEquals(0, refreshTokens.findAll().stream().filter(t -> t.getUser().getId().equals(testUser.getId())).count());
    }

    @Test
    void resettingThePassword_endsEverySession() throws Exception {
        String token = login().get("refreshToken").asText();
        String rawReset = java.util.UUID.randomUUID().toString();
        com.ajinkya.linky.entity.PasswordResetToken reset = com.ajinkya.linky.entity.PasswordResetToken.builder()
                .user(testUser).token(Hashing.sha256Hex(rawReset)).expiresAt(java.time.LocalDateTime.now().plusHours(1)).build();
        resetTokens.save(reset);

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawReset + "\",\"newPassword\":\"reset-pass-789\"}"))
                .andExpect(status().isOk());

        refresh(token).andExpect(status().isBadRequest());
    }

    @Test
    void logout_removesTheSession() throws Exception {
        String token = login().get("refreshToken").asText();
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}")).andExpect(status().isOk());
        refresh(token).andExpect(status().isBadRequest());
    }

    // ---------- avatar uploads

    @Test
    void avatar_realImagesAreAccepted_andStoredWithTheDetectedExtension() throws Exception {
        // the client calls it .html and claims text/html: only the bytes matter
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "evil.html", "text/html", PNG))
                        .header("Authorization", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(endsWith(".png")));
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "photo.bin", "application/octet-stream", JPEG))
                        .header("Authorization", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(endsWith(".jpg")));
    }

    @Test
    void avatar_nonImagesAreRejected_evenWithAnImageExtensionAndContentType() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        for (byte[] content : new byte[][]{html, svg, exe}) {
            mockMvc.perform(multipart("/api/users/me/avatar")
                            .file(new MockMultipartFile("file", "avatar.png", "image/png", content))
                            .header("Authorization", userToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("PNG, JPEG, GIF or WebP")));
        }
    }

    @Test
    void avatar_oversizedFilesAreRejected() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "big.png", "image/png", big))
                        .header("Authorization", userToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("too large")));
    }

    @Test
    void profileImage_mustBeAnUploadOrAnHttpsUrl() throws Exception {
        for (String bad : new String[]{"javascript:alert(1)", "http://insecure.example.com/a.png", "/etc/passwd", "data:text/html,x"}) {
            mockMvc.perform(put("/api/users/me").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"profileImage\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/users/me").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"profileImage\":\"/uploads/avatars/user_1_abc.png\"}"))
                .andExpect(status().isOk());
    }
}
