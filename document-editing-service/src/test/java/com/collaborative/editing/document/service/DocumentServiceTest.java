package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.model.Document;
import com.collaborative.editing.document.repository.ChangeTrackingRepository;
import com.collaborative.editing.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.document.model.ChangeTracking;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChangeTrackingRepository changeTrackingRepository;

    @Mock
    private com.collaborative.editing.document.client.VersionServiceClient versionServiceClient;

    @Mock
    private com.collaborative.editing.document.client.UserServiceClient userServiceClient;

    @InjectMocks
    private DocumentService documentService;

    private Document testDocument;
    private DocumentDTO testDocumentDTO;

    @BeforeEach
    void setUp() {
        testDocument = new Document();
        testDocument.setId(1L);
        testDocument.setTitle("Test Document");
        testDocument.setContent("Initial content");
        testDocument.setOwnerId(1L);
        testDocument.setCreatedAt(LocalDateTime.now());
        testDocument.setStatus("ACTIVE");

        testDocumentDTO = new DocumentDTO();
        testDocumentDTO.setTitle("Test Document");
        testDocumentDTO.setContent("Initial content");
        testDocumentDTO.setOwnerId(1L);
    }

    @Test
    void testCreateDocument_Success() {
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.createDocument(testDocumentDTO);

        assertNotNull(result);
        assertEquals("Test Document", result.getTitle());
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    void testEditDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.editDocument(1L, 1L, "Updated content");

        assertNotNull(result);
        verify(documentRepository, times(1)).save(any(Document.class));
        verify(changeTrackingRepository, times(1)).save(any(com.collaborative.editing.document.model.ChangeTracking.class));
    }

    @Test
    void testEditDocument_NoPermission() {
        testDocument.setOwnerId(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        assertThrows(RuntimeException.class, () -> {
            documentService.editDocument(1L, 1L, "Updated content");
        });
    }

    @Test
    void testAddCollaborator_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.addCollaborator(1L, 1L, 2L);

        assertNotNull(result);
        assertTrue(testDocument.getCollaboratorIds().contains(2L));
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    void testGetRealTimeChanges() {
        when(changeTrackingRepository.findByDocumentIdOrderByTimestampDesc(1L))
                .thenReturn(java.util.Collections.emptyList());

        var result = documentService.getRealTimeChanges(1L);

        assertNotNull(result);
        verify(changeTrackingRepository, times(1))
                .findByDocumentIdOrderByTimestampDesc(1L);
    }

    @Test
    @DisplayName("Create document with empty content - should handle version 0")
    void testCreateDocument_EmptyContent() {
        DocumentDTO emptyDoc = new DocumentDTO();
        emptyDoc.setTitle("Empty Document");
        emptyDoc.setContent("");
        emptyDoc.setOwnerId(1L);
        
        Document emptyDocument = new Document();
        emptyDocument.setId(2L);
        emptyDocument.setTitle("Empty Document");
        emptyDocument.setContent("");
        emptyDocument.setOwnerId(1L);
        emptyDocument.setCreatedAt(LocalDateTime.now());
        emptyDocument.setStatus("ACTIVE");
        
        when(documentRepository.save(any(Document.class))).thenReturn(emptyDocument);
        when(versionServiceClient.createInitialVersion(anyLong(), anyString(), anyLong()))
            .thenReturn(true);

        DocumentDTO result = documentService.createDocument(emptyDoc);

        assertNotNull(result);
        assertEquals("", result.getContent());
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Create document with collaborators")
    void testCreateDocument_WithCollaborators() {
        DocumentDTO docWithCollabs = new DocumentDTO();
        docWithCollabs.setTitle("Shared Document");
        docWithCollabs.setContent("Content");
        docWithCollabs.setOwnerId(1L);
        docWithCollabs.setCollaboratorIds(new HashSet<>(java.util.Arrays.asList(2L, 3L)));
        
        Document doc = new Document();
        doc.setId(3L);
        doc.setTitle("Shared Document");
        doc.setContent("Content");
        doc.setOwnerId(1L);
        doc.setCollaboratorIds(new java.util.HashSet<>(java.util.Arrays.asList(2L, 3L)));
        doc.setCreatedAt(LocalDateTime.now());
        doc.setStatus("ACTIVE");
        
        when(documentRepository.save(any(Document.class))).thenReturn(doc);
        when(versionServiceClient.createInitialVersion(anyLong(), anyString(), anyLong()))
            .thenReturn(true);

        DocumentDTO result = documentService.createDocument(docWithCollabs);

        assertNotNull(result);
        assertEquals(2, result.getCollaboratorIds().size());
        assertTrue(result.getCollaboratorIds().contains(2L));
        assertTrue(result.getCollaboratorIds().contains(3L));
    }

    @Test
    @DisplayName("Edit document with empty content - version 0 scenario")
    void testEditDocument_EmptyContent() {
        testDocument.setContent("");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.editDocument(1L, 1L, "New content");

        assertNotNull(result);
        assertEquals("New content", result.getContent());
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Remove collaborator successfully")
    void testRemoveCollaborator_Success() {
        testDocument.getCollaboratorIds().add(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.removeCollaborator(1L, 1L, 2L);

        assertNotNull(result);
        assertFalse(testDocument.getCollaboratorIds().contains(2L));
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Remove collaborator - not owner")
    void testRemoveCollaborator_NotOwner() {
        testDocument.setOwnerId(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        assertThrows(RuntimeException.class, () -> {
            documentService.removeCollaborator(1L, 1L, 2L);
        });
    }

    @Test
    @DisplayName("Get documents accessible by user - owner")
    void testGetDocumentsAccessibleByUser_Owner() {
        when(documentRepository.findByOwnerId(1L))
            .thenReturn(java.util.Arrays.asList(testDocument));

        List<DocumentDTO> result = documentService.getDocumentsAccessibleByUser(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("Get documents accessible by user - collaborator")
    void testGetDocumentsAccessibleByUser_Collaborator() {
        Document collaboratorDoc = new Document();
        collaboratorDoc.setId(2L);
        collaboratorDoc.setTitle("Collaborator Doc");
        collaboratorDoc.setContent("Content");
        collaboratorDoc.setOwnerId(2L);
        collaboratorDoc.getCollaboratorIds().add(1L);
        collaboratorDoc.setStatus("ACTIVE");
        
        when(documentRepository.findByOwnerId(1L)).thenReturn(java.util.Collections.emptyList());
        when(documentRepository.findDocumentsByCollaboratorId(1L))
            .thenReturn(java.util.Arrays.asList(collaboratorDoc));

        List<DocumentDTO> result = documentService.getDocumentsAccessibleByUser(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
    }

    @Test
    @DisplayName("Delete document successfully")
    void testDeleteDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        documentService.deleteDocument(1L, 1L);

        assertEquals("DELETED", testDocument.getStatus());
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Delete document - not owner")
    void testDeleteDocument_NotOwner() {
        testDocument.setOwnerId(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        assertThrows(RuntimeException.class, () -> {
            documentService.deleteDocument(1L, 1L);
        });
    }

    @Test
    @DisplayName("Get document by ID - not found")
    void testGetDocumentById_NotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            documentService.getDocumentById(999L);
        });
    }

    @Test
    @DisplayName("Link changes to version")
    void testLinkChangesToVersion() {
        ChangeTracking change = new ChangeTracking(1L, 1L, "INSERT", "content", 0);
        change.setId(1L);
        
        when(changeTrackingRepository.findByDocumentIdAndVersionIdIsNullOrderByTimestampAsc(1L))
            .thenReturn(java.util.Arrays.asList(change));
        when(changeTrackingRepository.save(any(ChangeTracking.class))).thenReturn(change);

        documentService.linkChangesToVersion(1L, 1L);

        verify(changeTrackingRepository, times(1)).save(any(ChangeTracking.class));
    }

    @Test
    @DisplayName("Get unversioned changes")
    void testGetUnversionedChanges() {
        ChangeTracking change = new ChangeTracking(1L, 1L, "INSERT", "content", 0);
        change.setId(1L);
        
        when(changeTrackingRepository.findByDocumentIdAndVersionIdIsNullOrderByTimestampAsc(1L))
            .thenReturn(java.util.Arrays.asList(change));

        List<ChangeTrackingDTO> result = documentService.getUnversionedChanges(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getDocumentId());
    }

    @Test
    @DisplayName("Track change successfully")
    void testTrackChange() {
        ChangeTracking change = new ChangeTracking(1L, 1L, "INSERT", "content", 0);
        change.setId(1L);
        
        when(changeTrackingRepository.save(any(ChangeTracking.class))).thenReturn(change);

        ChangeTrackingDTO result = documentService.trackChange(1L, 1L, "INSERT", "content", 0);

        assertNotNull(result);
        assertEquals(1L, result.getDocumentId());
        assertEquals("INSERT", result.getChangeType());
        verify(changeTrackingRepository, times(1)).save(any(ChangeTracking.class));
    }

    @Test
    @DisplayName("Add collaborator - user already collaborator")
    void testAddCollaborator_AlreadyCollaborator() {
        testDocument.getCollaboratorIds().add(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(userServiceClient.userExists(2L)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> {
            documentService.addCollaborator(1L, 1L, 2L);
        });
    }

    @Test
    @DisplayName("Add collaborator - user does not exist")
    void testAddCollaborator_UserNotExists() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(userServiceClient.userExists(999L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            documentService.addCollaborator(1L, 1L, 999L);
        });
    }

    @Test
    @DisplayName("Can user edit document - collaborator")
    void testCanUserEditDocument_Collaborator() {
        testDocument.getCollaboratorIds().add(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserEditDocument(1L, 2L);

        assertTrue(result);
    }

    @Test
    @DisplayName("Can user edit document - not authorized")
    void testCanUserEditDocument_NotAuthorized() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserEditDocument(1L, 999L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Get all documents")
    void testGetAllDocuments() {
        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setTitle("Doc 2");
        doc2.setContent("Content 2");
        doc2.setOwnerId(2L);
        doc2.setStatus("ACTIVE");
        
        when(documentRepository.findAll())
            .thenReturn(java.util.Arrays.asList(testDocument, doc2));

        List<DocumentDTO> result = documentService.getAllDocuments();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Get documents by owner")
    void testGetDocumentsByOwner() {
        when(documentRepository.findByOwnerId(1L))
            .thenReturn(java.util.Arrays.asList(testDocument));

        List<DocumentDTO> result = documentService.getDocumentsByOwner(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getOwnerId());
    }

    @Test
    @DisplayName("Can user access document - owner")
    void testCanUserAccessDocument_Owner() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserAccessDocument(1L, 1L);

        assertTrue(result);
    }

    @Test
    @DisplayName("Can user access document - collaborator")
    void testCanUserAccessDocument_Collaborator() {
        testDocument.getCollaboratorIds().add(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserAccessDocument(1L, 2L);

        assertTrue(result);
    }

    @Test
    @DisplayName("Can user access document - not authorized")
    void testCanUserAccessDocument_NotAuthorized() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserAccessDocument(1L, 999L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Can user access document - document not found")
    void testCanUserAccessDocument_DocumentNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = documentService.canUserAccessDocument(999L, 1L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Can user access document - deleted document")
    void testCanUserAccessDocument_DeletedDocument() {
        testDocument.setStatus("DELETED");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.canUserAccessDocument(1L, 1L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Get changes by version ID")
    void testGetChangesByVersionId() {
        ChangeTracking change = new ChangeTracking(1L, 1L, "INSERT", "content", 0);
        change.setId(1L);
        change.setVersionId(1L);
        
        when(changeTrackingRepository.findByVersionIdOrderByTimestampAsc(1L))
            .thenReturn(java.util.Arrays.asList(change));

        List<ChangeTrackingDTO> result = documentService.getChangesByVersionId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getDocumentId());
    }

    @Test
    @DisplayName("Get changes by version ID - empty list")
    void testGetChangesByVersionId_Empty() {
        when(changeTrackingRepository.findByVersionIdOrderByTimestampAsc(1L))
            .thenReturn(java.util.Collections.emptyList());

        List<ChangeTrackingDTO> result = documentService.getChangesByVersionId(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Unlink changes from versions")
    void testUnlinkChangesFromVersions() {
        ChangeTracking change1 = new ChangeTracking(1L, 1L, "INSERT", "content1", 0);
        change1.setId(1L);
        change1.setVersionId(1L);
        
        ChangeTracking change2 = new ChangeTracking(1L, 1L, "INSERT", "content2", 0);
        change2.setId(2L);
        change2.setVersionId(2L);
        
        when(changeTrackingRepository.findByVersionIdOrderByTimestampAsc(1L))
            .thenReturn(java.util.Arrays.asList(change1));
        when(changeTrackingRepository.findByVersionIdOrderByTimestampAsc(2L))
            .thenReturn(java.util.Arrays.asList(change2));
        when(changeTrackingRepository.save(any(ChangeTracking.class)))
            .thenReturn(change1, change2);

        documentService.unlinkChangesFromVersions(1L, java.util.Arrays.asList(1L, 2L));

        verify(changeTrackingRepository, times(2)).save(any(ChangeTracking.class));
    }

    @Test
    @DisplayName("Unlink changes from versions - empty version IDs")
    void testUnlinkChangesFromVersions_EmptyVersionIds() {
        documentService.unlinkChangesFromVersions(1L, java.util.Collections.emptyList());

        verify(changeTrackingRepository, never()).save(any(ChangeTracking.class));
    }

    @Test
    @DisplayName("Unlink changes from versions - no matching changes")
    void testUnlinkChangesFromVersions_NoMatchingChanges() {
        when(changeTrackingRepository.findByVersionIdOrderByTimestampAsc(1L))
            .thenReturn(java.util.Collections.emptyList());

        documentService.unlinkChangesFromVersions(1L, java.util.Arrays.asList(1L));

        verify(changeTrackingRepository, never()).save(any(ChangeTracking.class));
    }
}

