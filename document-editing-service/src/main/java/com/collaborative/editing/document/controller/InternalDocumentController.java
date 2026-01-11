package com.collaborative.editing.document.controller;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal REST API for inter-service communication
 * These endpoints are meant to be called by other microservices, not external clients
 */
@RestController
@RequestMapping("/internal/documents")
public class InternalDocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocumentController.class);
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private com.collaborative.editing.document.websocket.DocumentWebSocketController webSocketController;

    /**
     * Internal endpoint to get document by ID (for service-to-service calls)
     * @param id Document ID
     * @return DocumentDTO or error
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentByIdInternal(@PathVariable Long id) {
        if (id == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Document ID cannot be null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        try {
            DocumentDTO document = documentService.getDocumentById(id);
            return ResponseEntity.ok(document);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Internal endpoint to verify document exists
     * @param id Document ID
     * @return Boolean indicating if document exists
     */
    @GetMapping("/{id}/exists")
    public ResponseEntity<Map<String, Boolean>> documentExists(@PathVariable Long id) {
        Map<String, Boolean> response = new HashMap<>();
        if (id == null) {
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }
        try {
            DocumentDTO document = documentService.getDocumentById(id);
            response.put("exists", document != null);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Internal endpoint to update document content (for version control service)
     * @param id Document ID
     * @param content New document content
     * @return Updated DocumentDTO
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<?> updateDocumentContent(@PathVariable Long id, 
                                                  @RequestBody Map<String, String> request) {
        if (id == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Document ID cannot be null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        try {
            String content = request.get("content");
            if (content == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Content is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Get document to find owner
            DocumentDTO document = documentService.getDocumentById(id);
            if (document == null || document.getOwnerId() == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Document not found or invalid");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            Long ownerId = Objects.requireNonNull(document.getOwnerId(), "Owner ID cannot be null");
            DocumentDTO updatedDocument = documentService.editDocument(id, ownerId, content);
            return ResponseEntity.ok(updatedDocument);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Internal endpoint to get unversioned changes for a document
     * @param id Document ID
     * @return List of unversioned changes
     */
    @GetMapping("/{id}/unversioned-changes")
    public ResponseEntity<List<ChangeTrackingDTO>> getUnversionedChanges(@PathVariable Long id) {
        List<ChangeTrackingDTO> changes = documentService.getUnversionedChanges(id);
        return ResponseEntity.ok(changes);
    }

    /**
     * Internal endpoint to link changes to a version
     * @param id Document ID
     * @param request Map containing versionId
     * @return Success response
     */
    @PostMapping("/{id}/link-changes-to-version")
    public ResponseEntity<?> linkChangesToVersion(@PathVariable Long id,
                                                   @RequestBody Map<String, Long> request) {
        Long versionId = request.get("versionId");
        if (versionId == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Version ID is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        documentService.linkChangesToVersion(id, versionId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Changes linked to version successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Internal endpoint to unlink changes from versions (for revert)
     * @param id Document ID
     * @param request Map containing list of versionIds
     * @return Success response
     */
    @PostMapping("/{id}/unlink-changes-from-versions")
    public ResponseEntity<?> unlinkChangesFromVersions(@PathVariable Long id,
                                                        @RequestBody Map<String, List<Long>> request) {
        List<Long> versionIds = request.get("versionIds");
        if (versionIds == null || versionIds.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Version IDs are required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        documentService.unlinkChangesFromVersions(id, versionIds);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Changes unlinked from versions successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Internal endpoint to delete all documents owned by a user
     * Used when a user account is deleted
     * @param ownerId Owner ID
     * @return Success response
     */
    @DeleteMapping("/owner/{ownerId}")
    public ResponseEntity<?> deleteAllDocumentsByOwner(@PathVariable Long ownerId) {
        if (ownerId == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Owner ID cannot be null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        try {
            // Get all documents owned by this user
            List<DocumentDTO> documents = documentService.getDocumentsByOwner(ownerId);
            
            // Delete each document (set status to DELETED)
            int deletedCount = 0;
            for (DocumentDTO doc : documents) {
                if (doc.getStatus() == null || !"DELETED".equals(doc.getStatus())) {
                    try {
                        Long docId = doc.getId();
                        if (docId != null) {
                            documentService.deleteDocument(docId, ownerId);
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        // Log but continue with other documents
                        logger.warn("Failed to delete document {} for owner {}: {}", 
                                doc.getId(), ownerId, e.getMessage());
                    }
                }
            }
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Deleted " + deletedCount + " document(s) for owner " + ownerId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting documents for owner: {}", ownerId, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete documents: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Internal endpoint to get changes for a specific version
     * @param versionId Version ID
     * @return List of changes for the version
     */
    @GetMapping("/version/{versionId}/changes")
    public ResponseEntity<List<ChangeTrackingDTO>> getChangesByVersionId(@PathVariable Long versionId) {
        List<ChangeTrackingDTO> changes = documentService.getChangesByVersionId(versionId);
        return ResponseEntity.ok(changes);
    }

    /**
     * Internal endpoint to reset WebSocket state for a document
     * Used after revert or content update to ensure real-time editing works correctly
     * @param id Document ID
     * @return Success response
     */
    @PostMapping("/{id}/reset-websocket-state")
    public ResponseEntity<?> resetWebSocketState(@PathVariable Long id) {
        try {
            webSocketController.resetDocumentState(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "WebSocket state reset successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to reset WebSocket state: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Internal health check endpoint
     * @return Service status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "document-editing-service");
        return ResponseEntity.ok(status);
    }
}

