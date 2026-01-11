package com.collaborative.editing.version.service;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.VersionDTO;
import com.collaborative.editing.version.client.DocumentServiceClient;
import com.collaborative.editing.version.model.DocumentVersion;
import com.collaborative.editing.version.model.UserContribution;
import com.collaborative.editing.version.repository.DocumentVersionRepository;
import com.collaborative.editing.version.repository.UserContributionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Version Control Service Enhanced Tests")
class VersionControlServiceEnhancedTest {

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private UserContributionRepository contributionRepository;

    @Mock
    private DocumentServiceClient documentServiceClient;

    @InjectMocks
    private VersionControlService versionControlService;

    private DocumentVersion version0;
    private DocumentVersion version1;
    private Long documentId;
    private Long userId;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId = 1L;

        version0 = new DocumentVersion();
        version0.setId(1L);
        version0.setDocumentId(documentId);
        version0.setVersionNumber(0L);
        version0.setContent("");
        version0.setCreatedBy(userId);
        version0.setCreatedAt(LocalDateTime.now());
        version0.setChangeDescription("Initial document creation");

        version1 = new DocumentVersion();
        version1.setId(2L);
        version1.setDocumentId(documentId);
        version1.setVersionNumber(1L);
        version1.setContent("Hello");
        version1.setCreatedBy(userId);
        version1.setCreatedAt(LocalDateTime.now());
        version1.setChangeDescription("Document edited");
    }

    @Test
    @DisplayName("Create initial version 0 - empty document")
    void testCreateInitialVersion_EmptyDocument() {
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(version0);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.createInitialVersion(documentId, "", userId);

        assertNotNull(result);
        assertEquals(0L, result.getVersionNumber());
        assertEquals("", result.getContent());
        verify(versionRepository, times(1)).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Create initial version 0 - version already exists")
    void testCreateInitialVersion_AlreadyExists() {
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));

        VersionDTO result = versionControlService.createInitialVersion(documentId, "", userId);

        assertNotNull(result);
        assertEquals(0L, result.getVersionNumber());
        verify(versionRepository, never()).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Create version 1 from version 0")
    void testCreateVersion_FromVersion0() {
        ChangeTrackingDTO change = new ChangeTrackingDTO();
        change.setDocumentId(documentId);
        change.setUserId(userId);
        change.setChangeType("INSERT");
        change.setContent("Hello");

        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));
        when(versionRepository.countByDocumentId(documentId)).thenReturn(1L);
        when(documentServiceClient.getUnversionedChanges(documentId))
            .thenReturn(Collections.singletonList(change));
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(version1);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.createVersion(documentId, "Hello", userId, "First edit");

        assertNotNull(result);
        assertEquals(1L, result.getVersionNumber());
        assertEquals("Hello", result.getContent());
        verify(versionRepository, times(1)).save(any(DocumentVersion.class));
        verify(documentServiceClient, times(1)).linkChangesToVersion(documentId, version1.getId());
    }

    @Test
    @DisplayName("Create version - no changes detected")
    void testCreateVersion_NoChanges() {
        when(documentServiceClient.getUnversionedChanges(documentId))
            .thenReturn(Collections.emptyList());
        when(versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Optional.of(version1));

        assertThrows(RuntimeException.class, () -> {
            versionControlService.createVersion(documentId, "Hello", userId, "No changes");
        });
    }

    @Test
    @DisplayName("Get version history - multiple versions")
    void testGetVersionHistory_MultipleVersions() {
        DocumentVersion version2 = new DocumentVersion();
        version2.setId(3L);
        version2.setDocumentId(documentId);
        version2.setVersionNumber(2L);
        version2.setContent("Hello World");
        version2.setCreatedBy(userId);

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Arrays.asList(version2, version1, version0));

        List<VersionDTO> result = versionControlService.getVersionHistory(documentId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(2L, result.get(0).getVersionNumber()); // Latest first
        assertEquals(0L, result.get(2).getVersionNumber()); // Oldest last
    }

    @Test
    @DisplayName("Get version by number - version 0")
    void testGetVersionByNumber_Version0() {
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));

        VersionDTO result = versionControlService.getVersionByNumber(documentId, 0L);

        assertNotNull(result);
        assertEquals(0L, result.getVersionNumber());
        assertEquals("", result.getContent());
    }

    @Test
    @DisplayName("Get version by number - not found")
    void testGetVersionByNumber_NotFound() {
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 999L))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            versionControlService.getVersionByNumber(documentId, 999L);
        });
    }

    @Test
    @DisplayName("Revert to version 0")
    void testRevertToVersion_Version0() {
        com.collaborative.editing.common.dto.DocumentDTO updatedDoc = 
            new com.collaborative.editing.common.dto.DocumentDTO();
        updatedDoc.setId(documentId);
        updatedDoc.setContent("");
        
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));
        when(versionRepository.countByDocumentId(documentId)).thenReturn(1L);
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(version0);
        when(documentServiceClient.updateDocumentContent(documentId, ""))
            .thenReturn(updatedDoc);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.revertToVersion(documentId, 0L, userId);

        assertNotNull(result);
        assertEquals(0L, result.getVersionNumber());
        verify(documentServiceClient, times(1)).updateDocumentContent(documentId, "");
    }

    @Test
    @DisplayName("Revert to version - version not found")
    void testRevertToVersion_NotFound() {
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 999L))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            versionControlService.revertToVersion(documentId, 999L, userId);
        });
    }

    @Test
    @DisplayName("Get user contributions - multiple users")
    void testGetUserContributions_MultipleUsers() {
        UserContribution contrib1 = new UserContribution();
        contrib1.setDocumentId(documentId);
        contrib1.setUserId(1L);
        contrib1.setEditCount(10);
        contrib1.setCharactersAdded(100);

        UserContribution contrib2 = new UserContribution();
        contrib2.setDocumentId(documentId);
        contrib2.setUserId(2L);
        contrib2.setEditCount(5);
        contrib2.setCharactersAdded(50);

        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Arrays.asList(contrib1, contrib2));

        var result = versionControlService.getUserContributions(documentId);

        assertNotNull(result);
        assertEquals(documentId, result.get("documentId"));
        assertNotNull(result.get("contributions"));
    }

    @Test
    @DisplayName("Create version - with version 0 existing")
    void testCreateVersion_WithVersion0Existing() {
        ChangeTrackingDTO change = new ChangeTrackingDTO();
        change.setDocumentId(documentId);
        change.setUserId(userId);
        change.setChangeType("INSERT");
        change.setContent("Hello");

        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));
        when(versionRepository.countByDocumentId(documentId)).thenReturn(1L);
        when(documentServiceClient.getUnversionedChanges(documentId))
            .thenReturn(Collections.singletonList(change));
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(version1);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.createVersion(documentId, "Hello", userId, "Edit");

        assertNotNull(result);
        assertEquals(1L, result.getVersionNumber());
    }

    @Test
    @DisplayName("Create version - without version 0")
    void testCreateVersion_WithoutVersion0() {
        ChangeTrackingDTO change = new ChangeTrackingDTO();
        change.setDocumentId(documentId);
        change.setUserId(userId);
        change.setChangeType("INSERT");
        change.setContent("Hello");

        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.empty());
        when(versionRepository.countByDocumentId(documentId)).thenReturn(0L);
        when(documentServiceClient.getUnversionedChanges(documentId))
            .thenReturn(Collections.singletonList(change));
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(version1);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.createVersion(documentId, "Hello", userId, "Edit");

        assertNotNull(result);
        assertEquals(1L, result.getVersionNumber());
    }

    @Test
    @DisplayName("Get version history - empty")
    void testGetVersionHistory_Empty() {
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Collections.emptyList());

        List<VersionDTO> result = versionControlService.getVersionHistory(documentId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Revert to version - creates new version")
    void testRevertToVersion_CreatesNewVersion() {
        DocumentVersion newVersion = new DocumentVersion();
        newVersion.setId(3L);
        newVersion.setDocumentId(documentId);
        newVersion.setVersionNumber(2L);
        newVersion.setContent("");
        newVersion.setCreatedBy(userId);

        com.collaborative.editing.common.dto.DocumentDTO updatedDoc = 
            new com.collaborative.editing.common.dto.DocumentDTO();
        updatedDoc.setId(documentId);
        updatedDoc.setContent("");
        
        when(versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L))
            .thenReturn(Optional.of(version0));
        when(versionRepository.countByDocumentId(documentId)).thenReturn(2L);
        when(versionRepository.save(any(DocumentVersion.class))).thenReturn(newVersion);
        when(documentServiceClient.updateDocumentContent(documentId, ""))
            .thenReturn(updatedDoc);
        when(contributionRepository.findByDocumentIdAndUserId(documentId, userId))
            .thenReturn(Optional.empty());
        when(contributionRepository.save(any(UserContribution.class)))
            .thenReturn(new UserContribution());

        VersionDTO result = versionControlService.revertToVersion(documentId, 0L, userId);

        assertNotNull(result);
        assertEquals(2L, result.getVersionNumber()); // New version created
        verify(versionRepository, times(1)).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Delete all versions for document - with versions and contributions")
    void testDeleteAllVersionsForDocument_WithData() {
        DocumentVersion v1 = new DocumentVersion();
        v1.setId(1L);
        v1.setDocumentId(documentId);
        v1.setVersionNumber(1L);

        DocumentVersion v2 = new DocumentVersion();
        v2.setId(2L);
        v2.setDocumentId(documentId);
        v2.setVersionNumber(2L);

        UserContribution contrib = new UserContribution();
        contrib.setDocumentId(documentId);
        contrib.setUserId(userId);

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Arrays.asList(v2, v1));
        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Arrays.asList(contrib));
        doNothing().when(versionRepository).deleteAll(anyList());
        doNothing().when(contributionRepository).deleteAll(anyList());

        versionControlService.deleteAllVersionsForDocument(documentId);

        verify(versionRepository, times(1)).deleteAll(anyList());
        verify(contributionRepository, times(1)).deleteAll(anyList());
    }

    @Test
    @DisplayName("Delete all versions for document - no versions")
    void testDeleteAllVersionsForDocument_NoVersions() {
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Collections.emptyList());
        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Collections.emptyList());

        versionControlService.deleteAllVersionsForDocument(documentId);

        verify(versionRepository, never()).deleteAll(anyList());
        verify(contributionRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("Delete all versions for document - versions but no contributions")
    void testDeleteAllVersionsForDocument_NoContributions() {
        DocumentVersion v1 = new DocumentVersion();
        v1.setId(1L);
        v1.setDocumentId(documentId);
        v1.setVersionNumber(1L);

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
            .thenReturn(Arrays.asList(v1));
        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Collections.emptyList());
        doNothing().when(versionRepository).deleteAll(anyList());

        versionControlService.deleteAllVersionsForDocument(documentId);

        verify(versionRepository, times(1)).deleteAll(anyList());
        verify(contributionRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("Get user contributions - empty contributions")
    void testGetUserContributions_Empty() {
        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Collections.emptyList());

        var result = versionControlService.getUserContributions(documentId);

        assertNotNull(result);
        assertEquals(documentId, result.get("documentId"));
        assertNotNull(result.get("contributions"));
    }

    @Test
    @DisplayName("Get user contributions - single user")
    void testGetUserContributions_SingleUser() {
        UserContribution contrib = new UserContribution();
        contrib.setDocumentId(documentId);
        contrib.setUserId(userId);
        contrib.setEditCount(5);
        contrib.setCharactersAdded(50);

        when(contributionRepository.findByDocumentId(documentId))
            .thenReturn(Collections.singletonList(contrib));

        var result = versionControlService.getUserContributions(documentId);

        assertNotNull(result);
        assertEquals(documentId, result.get("documentId"));
        assertNotNull(result.get("contributions"));
    }
}

