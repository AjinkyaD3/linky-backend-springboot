package com.ajinkya.linky.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class BioPageControllerTest extends BaseIntegrationTest {

    @Autowired private ObjectMapper mapper;

    private String page(String slug, String... urls) {
        StringBuilder links = new StringBuilder();
        for (int i = 0; i < urls.length; i++) {
            links.append(i == 0 ? "" : ",").append("{\"label\":\"Link ").append(i).append("\",\"url\":\"").append(urls[i]).append("\"}");
        }
        return "{\"slug\":\"" + slug + "\",\"title\":\"My links\",\"bio\":\"Hello\",\"links\":[" + links + "]}";
    }

    private JsonNode create(String body) throws Exception {
        String res = mockMvc.perform(post("/api/pages").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    @Test
    void create_thenPublicViewShowsLabelsAndShortLinksOnly() throws Exception {
        JsonNode created = create(page("ajinkya-demo", "https://github.com/a", "https://example.com/b"));
        assertEquals(2, created.get("links").size());
        String code = created.get("links").get(0).get("shortCode").asText();

        mockMvc.perform(get("/api/public/pages/ajinkya-demo"))   // no auth
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My links"))
                .andExpect(jsonPath("$.links[0].shortCode").value(code))
                .andExpect(jsonPath("$.links[0].url").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());

        // the button is a real tracked short link
        mockMvc.perform(get("/" + code)).andExpect(header().string("Location", "https://github.com/a"));
    }

    @Test
    void update_reusesShortLinkForUnchangedUrl_andCreatesNewOnes() throws Exception {
        JsonNode created = create(page("reuse-me", "https://example.com/keep"));
        long id = created.get("id").asLong();
        String keptCode = created.get("links").get(0).get("shortCode").asText();

        String res = mockMvc.perform(put("/api/pages/" + id).header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(page("reuse-me", "https://example.com/new", "https://example.com/keep")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode updated = mapper.readTree(res);

        assertEquals(2, updated.get("links").size());
        assertEquals("https://example.com/new", updated.get("links").get(0).get("url").asText());
        assertEquals(keptCode, updated.get("links").get(1).get("shortCode").asText(), "unchanged URL keeps its short link and analytics");
    }

    @Test
    void validation_andUniqueness() throws Exception {
        create(page("taken-slug", "https://example.com/a"));

        mockMvc.perform(post("/api/pages").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content(page("taken-slug", "https://example.com/a"))).andExpect(status().isConflict());
        for (String bad : new String[]{"Bad_Slug", "ab", "-leading", "trailing-", "has space"}) {
            mockMvc.perform(post("/api/pages").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                    .content(page(bad, "https://example.com/a"))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/pages").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content(page("js-link", "javascript:alert(1)"))).andExpect(status().isBadRequest());
    }

    @Test
    void ownership_andAuth() throws Exception {
        long id = create(page("owned-page", "https://example.com/a")).get("id").asLong();

        mockMvc.perform(put("/api/pages/" + id).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(page("owned-page", "https://evil.example.com"))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/pages/" + id).header("Authorization", adminToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pages")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pages").contentType(MediaType.APPLICATION_JSON)
                .content(page("anon-page", "https://example.com/a"))).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/pages").header("Authorization", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("owned-page"));
    }

    @Test
    void delete_removesPublicPage_andPageLimitEnforced() throws Exception {
        long id = create(page("temp-page", "https://example.com/a")).get("id").asLong();
        mockMvc.perform(delete("/api/pages/" + id).header("Authorization", userToken)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/pages/temp-page")).andExpect(status().isNotFound());

        for (int i = 0; i < 5; i++) create(page("limit-page-" + i, "https://example.com/" + i));
        mockMvc.perform(post("/api/pages").header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON)
                .content(page("limit-page-6", "https://example.com/6"))).andExpect(status().isBadRequest());
    }
}
