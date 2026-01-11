package com.collaborative.editing.document.controller;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.service.DocumentService;
import com.collaborative.editing.document.websocket.DocumentWebSocketController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalDocumentController.class)
@DisplayName("Internal Document Controller Tests")
@SuppressWarnings("null")
class InternalDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentWebSocketController webSocketController;

    @Autowired
    private ObjectMapper objectMapper;

    private DocumentDTO testDocument;

    @BeforeEach
    void setUp() {
        testDocument = new DocumentDTO();
        testDocument.setId(1L);
        testDocument.setTitle("Test Document");
        testDocument.setContent("Test content");
        testDocument.setOwnerId(1L);
        testDocument.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Get document by ID - success")
    void testGetDocumentByIdInternal_Success() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(testDocument);

        mockMvc.perform(get("/internal/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Test Document"));
    }

    @Test
    @DisplayName("Get document by ID - null ID")
    void testGetDocumentByIdInternal_NullId() throws Exception {
        mockMvc.perform(get("/internal/documents/null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get document by ID - not found")
    void testGetDocumentByIdInternal_NotFound() throws Exception {
        when(documentService.getDocumentById(999L))
            .thenThrow(new RuntimeException("Document not found"));

        mockMvc.perform(get("/internal/documents/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Document exists - true")
    void testDocumentExists_True() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(testDocument);

        mockMvc.perform(get("/internal/documents/1/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    @DisplayName("Document exists - false")
    void testDocumentExists_False() throws Exception {
        when(documentService.getDocumentById(999L))
            .thenThrow(new RuntimeException("Document not found"));

        mockMvc.perform(get("/internal/documents/999/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    @DisplayName("Document exists - null ID")
    void testDocumentExists_NullId() throws Exception {
        mockMvc.perform(get("/internal/documents/null/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    @DisplayName("Update document content - success")
    void testUpdateDocumentContent_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("content", "Updated content");

        DocumentDTO updatedDoc = new DocumentDTO();
        updatedDoc.setId(1L);
        updatedDoc.setContent("Updated content");
        updatedDoc.setOwnerId(1L);

        when(documentService.getDocumentById(1L)).thenReturn(testDocument);
        when(documentService.editDocument(eq(1L), eq(1L), eq("Updated content")))
            .thenReturn(updatedDoc);

        mockMvc.perform(put("/internal/documents/1/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"));
    }

    @Test
    @DisplayName("Update document content - null ID")
    void testUpdateDocumentContent_NullId() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("content", "Updated content");

        mockMvc.perform(put("/internal/documents/null/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update document content - missing content")
    void testUpdateDocumentContent_MissingContent() throws Exception {
        Map<String, String> request = new HashMap<>();

        when(documentService.getDocumentById(1L)).thenReturn(testDocument);

        mockMvc.perform(put("/internal/documents/1/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get unversioned changes")
    void testGetUnversionedChanges() throws Exception {
        ChangeTrackingDTO change = new ChangeTrackingDTO();
        change.setDocumentId(1L);
        change.setUserId(1L);
        change.setChangeType("INSERT");

        when(documentService.getUnversionedChanges(1L))
            .thenReturn(Arrays.asList(change));

        mockMvc.perform(get("/internal/documents/1/unversioned-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].documentId").value(1L));
    }

    @Test
    @DisplayName("Link changes to version - success")
    void testLinkChangesToVersion_Success() throws Exception {
        Map<String, Long> request = new HashMap<>();
        request.put("versionId", 1L);

        doNothing().when(documentService).linkChangesToVersion(eq(1L), eq(1L));

        mockMvc.perform(post("/internal/documents/1/link-changes-to-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes linked to version successfully"));
    }

    @Test
    @DisplayName("Link changes to version - missing versionId")
    void testLinkChangesToVersion_MissingVersionId() throws Exception {
        Map<String, Long> request = new HashMap<>();

        mockMvc.perform(post("/internal/documents/1/link-changes-to-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Unlink changes from versions - success")
    void testUnlinkChangesFromVersions_Success() throws Exception {
        Map<String, List<Long>> request = new HashMap<>();
        request.put("versionIds", Arrays.asList(1L, 2L));

        doNothing().when(documentService).unlinkChangesFromVersions(eq(1L), anyList());

        mockMvc.perform(post("/internal/documents/1/unlink-changes-from-versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changes unlinked from versions successfully"));
    }

    @Test
    @DisplayName("Unlink changes from versions - empty versionIds")
    void testUnlinkChangesFromVersions_EmptyVersionIds() throws Exception {
        Map<String, List<Long>> request = new HashMap<>();
        request.put("versionIds", Arrays.asList());

        mockMvc.perform(post("/internal/documents/1/unlink-changes-from-versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Delete all documents by owner - success")
    void testDeleteAllDocumentsByOwner_Success() throws Exception {
        DocumentDTO doc1 = new DocumentDTO();
        doc1.setId(1L);
        doc1.setOwnerId(1L);
        doc1.setStatus("ACTIVE");

        when(documentService.getDocumentsByOwner(1L)).thenReturn(Arrays.asList(doc1));
        doNothing().when(documentService).deleteDocument(eq(1L), eq(1L));

        mockMvc.perform(delete("/internal/documents/owner/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Delete all documents by owner - null ownerId")
    void testDeleteAllDocumentsByOwner_NullOwnerId() throws Exception {
        mockMvc.perform(delete("/internal/documents/owner/null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get changes by version ID")
    void testGetChangesByVersionId() throws Exception {
        ChangeTrackingDTO change = new ChangeTrackingDTO();
        change.setDocumentId(1L);
        change.setUserId(1L);
        change.setChangeType("INSERT");

        when(documentService.getChangesByVersionId(1L))
            .thenReturn(Arrays.asList(change));

        mockMvc.perform(get("/internal/documents/version/1/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].documentId").value(1L));
    }

    @Test
    @DisplayName("Reset WebSocket state - success")
    void testResetWebSocketState_Success() throws Exception {
        doNothing().when(webSocketController).resetDocumentState(eq(1L));

        mockMvc.perform(post("/internal/documents/1/reset-websocket-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("WebSocket state reset successfully"));
    }

    @Test
    @DisplayName("Health check")
    void testHealth() throws Exception {
        mockMvc.perform(get("/internal/documents/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("document-editing-service"));
    }
}
