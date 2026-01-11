package com.collaborative.editing.version.controller;

import com.collaborative.editing.common.dto.VersionDTO;
import com.collaborative.editing.version.service.VersionControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalVersionController.class)
@DisplayName("Internal Version Controller Tests")
@SuppressWarnings("null")
class InternalVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VersionControlService versionControlService;

    @Autowired
    private ObjectMapper objectMapper;

    private VersionDTO testVersion;

    @BeforeEach
    void setUp() {
        testVersion = new VersionDTO();
        testVersion.setId(1L);
        testVersion.setDocumentId(1L);
        testVersion.setVersionNumber(0L);
        testVersion.setContent("");
        testVersion.setCreatedBy(1L);
    }

    @Test
    @DisplayName("Create initial version - success")
    void testCreateInitialVersion_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("documentId", 1L);
        request.put("content", "");
        request.put("createdBy", 1L);

        when(versionControlService.createInitialVersion(eq(1L), eq(""), eq(1L)))
            .thenReturn(testVersion);

        mockMvc.perform(post("/internal/versions/initial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.versionNumber").value(0L));
    }

    @Test
    @DisplayName("Create initial version - with content")
    void testCreateInitialVersion_WithContent() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("documentId", 1L);
        request.put("content", "Hello World");
        request.put("createdBy", 1L);

        testVersion.setContent("Hello World");
        when(versionControlService.createInitialVersion(eq(1L), eq("Hello World"), eq(1L)))
            .thenReturn(testVersion);

        mockMvc.perform(post("/internal/versions/initial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello World"));
    }

    @Test
    @DisplayName("Create initial version - missing documentId")
    void testCreateInitialVersion_MissingDocumentId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("content", "");
        request.put("createdBy", 1L);

        mockMvc.perform(post("/internal/versions/initial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create initial version - missing createdBy")
    void testCreateInitialVersion_MissingCreatedBy() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("documentId", 1L);
        request.put("content", "");

        mockMvc.perform(post("/internal/versions/initial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create initial version - exception handling")
    void testCreateInitialVersion_Exception() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("documentId", 1L);
        request.put("content", "");
        request.put("createdBy", 1L);

        when(versionControlService.createInitialVersion(anyLong(), anyString(), anyLong()))
            .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(post("/internal/versions/initial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Delete all versions for document - success")
    void testDeleteAllVersionsForDocument_Success() throws Exception {
        doNothing().when(versionControlService).deleteAllVersionsForDocument(eq(1L));

        mockMvc.perform(delete("/internal/versions/document/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All versions deleted successfully for document 1"));
    }

    @Test
    @DisplayName("Delete all versions for document - exception handling")
    void testDeleteAllVersionsForDocument_Exception() throws Exception {
        doThrow(new RuntimeException("Database error"))
            .when(versionControlService).deleteAllVersionsForDocument(eq(1L));

        mockMvc.perform(delete("/internal/versions/document/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Health check")
    void testHealth() throws Exception {
        mockMvc.perform(get("/internal/versions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("version-control-service"));
    }
}
