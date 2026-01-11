package com.collaborative.editing.document.controller;

import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.service.DocumentService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@DisplayName("Document Controller Enhanced Tests")
class DocumentControllerEnhancedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

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
    @DisplayName("Create document - empty content (version 0)")
    void testCreateDocument_EmptyContent() throws Exception {
        DocumentDTO emptyDoc = new DocumentDTO();
        emptyDoc.setTitle("Empty Document");
        emptyDoc.setContent("");
        emptyDoc.setOwnerId(1L);

        DocumentDTO savedDoc = new DocumentDTO();
        savedDoc.setId(1L);
        savedDoc.setTitle("Empty Document");
        savedDoc.setContent("");

        when(documentService.createDocument(any(DocumentDTO.class))).thenReturn(savedDoc);

        mockMvc.perform(post("/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyDoc)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.content").value(""));
    }

    @Test
    @DisplayName("Create document - with collaborators")
    void testCreateDocument_WithCollaborators() throws Exception {
        DocumentDTO docWithCollabs = new DocumentDTO();
        docWithCollabs.setTitle("Shared Document");
        docWithCollabs.setContent("Content");
        docWithCollabs.setOwnerId(1L);
        docWithCollabs.setCollaboratorIds(new HashSet<>(Arrays.asList(2L, 3L)));

        DocumentDTO savedDoc = new DocumentDTO();
        savedDoc.setId(1L);
        savedDoc.setTitle("Shared Document");
        savedDoc.setCollaboratorIds(new HashSet<>(Arrays.asList(2L, 3L)));

        when(documentService.createDocument(any(DocumentDTO.class))).thenReturn(savedDoc);

        mockMvc.perform(post("/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docWithCollabs)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collaboratorIds").isArray());
    }

    @Test
    @DisplayName("Edit document - version 0 scenario")
    void testEditDocument_Version0() throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("content", "New content");

        DocumentDTO updatedDoc = new DocumentDTO();
        updatedDoc.setId(1L);
        updatedDoc.setContent("New content");

        when(documentService.editDocument(eq(1L), eq(1L), eq("New content"))).thenReturn(updatedDoc);

        mockMvc.perform(put("/documents/1")
                .param("userId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("New content"));
    }

    @Test
    @DisplayName("Edit document - permission denied")
    void testEditDocument_PermissionDenied() throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("content", "New content");

        when(documentService.editDocument(eq(1L), eq(999L), anyString()))
            .thenThrow(new RuntimeException("User does not have permission"));

        mockMvc.perform(put("/documents/1")
                .param("userId", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Add collaborator")
    void testAddCollaborator() throws Exception {
        DocumentDTO updatedDoc = new DocumentDTO();
        updatedDoc.setId(1L);
        updatedDoc.getCollaboratorIds().add(2L);

        when(documentService.addCollaborator(eq(1L), eq(1L), eq(2L))).thenReturn(updatedDoc);

        mockMvc.perform(post("/documents/1/collaborators")
                .param("ownerId", "1")
                .param("collaboratorId", "2"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Remove collaborator")
    void testRemoveCollaborator() throws Exception {
        DocumentDTO updatedDoc = new DocumentDTO();
        updatedDoc.setId(1L);

        when(documentService.removeCollaborator(eq(1L), eq(1L), eq(2L))).thenReturn(updatedDoc);

        mockMvc.perform(delete("/documents/1/collaborators/2")
                .param("ownerId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Get document by ID")
    void testGetDocumentById() throws Exception {
        when(documentService.getDocumentById(1L)).thenReturn(testDocument);

        mockMvc.perform(get("/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Test Document"));
    }

    @Test
    @DisplayName("Get documents accessible by user")
    void testGetDocumentsAccessibleByUser() throws Exception {
        List<DocumentDTO> documents = Arrays.asList(testDocument);

        when(documentService.getDocumentsAccessibleByUser(eq(1L))).thenReturn(documents);

        mockMvc.perform(get("/documents")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    @DisplayName("Delete document")
    void testDeleteDocument() throws Exception {
        doNothing().when(documentService).deleteDocument(eq(1L), eq(1L));

        mockMvc.perform(delete("/documents/1")
                .param("userId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Get real-time changes")
    void testGetRealTimeChanges() throws Exception {
        when(documentService.getRealTimeChanges(eq(1L))).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/documents/1/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

