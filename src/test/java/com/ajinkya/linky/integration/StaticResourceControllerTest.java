package com.ajinkya.linky.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.junit.jupiter.api.Assertions.*;

public class StaticResourceControllerTest extends BaseIntegrationTest {

    private Path testUploadDir;
    private Path testFile;

    @BeforeEach
    void setUp() throws Exception {
        testUploadDir = Paths.get("uploads");
        Files.createDirectories(testUploadDir);
        testFile = testUploadDir.resolve("test-resource.txt");
        Files.write(testFile, "Hello Static Content".getBytes());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
    }

    @Test
    void getUploads_ExistingResource_ReturnsContent() throws Exception {
        MvcResult result = mockMvc.perform(get("/uploads/test-resource.txt"))
                .andExpect(status().isOk())
                .andReturn();
                
        assertEquals("Hello Static Content", result.getResponse().getContentAsString());
    }

    @Test
    void getUploads_NonexistentResource_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/uploads/does-not-exist.txt"))
                .andExpect(status().isNotFound());
    }
}
