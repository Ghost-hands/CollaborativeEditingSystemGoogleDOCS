package com.collaborative.editing.version.controller;

import com.collaborative.editing.common.dto.VersionDTO;
import com.collaborative.editing.version.service.VersionControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VersionControlController.class)
@SuppressWarnings("null")
class VersionControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VersionControlService versionControlService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateVersion() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("documentId", 1L);
        request.put("content", "Test content");
        request.put("createdBy", 1L);
        request.put("changeDescription", "Initial version");

        VersionDTO versionDTO = new VersionDTO();
        versionDTO.setId(1L);
        versionDTO.setDocumentId(1L);
        versionDTO.setVersionNumber(1L);

        when(versionControlService.createVersion(any(Long.class), any(String.class),
                any(Long.class), any(String.class))).thenReturn(versionDTO);

        String jsonContent = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent != null ? jsonContent : ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void testGetVersionHistory() throws Exception {
        List<VersionDTO> versions = Collections.emptyList();
        when(versionControlService.getVersionHistory(1L)).thenReturn(versions);

        mockMvc.perform(get("/versions/document/1/history"))
                .andExpect(status().isOk());
    }

    @Test
    void testRevertToVersion() throws Exception {
        VersionDTO versionDTO = new VersionDTO();
        versionDTO.setId(2L);
        versionDTO.setVersionNumber(2L);

        when(versionControlService.revertToVersion(1L, 1L, 1L)).thenReturn(versionDTO);

        mockMvc.perform(post("/versions/document/1/revert/1")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2L));
    }
}

