package com.collaborative.editing.version.controller;

import com.collaborative.editing.common.dto.VersionDTO;
import com.collaborative.editing.version.service.VersionControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal REST API for inter-service communication
 * These endpoints are meant to be called by other microservices, not external clients
 */
@RestController
@RequestMapping("/internal/versions")
public class InternalVersionController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalVersionController.class);
    
    @Autowired
    private VersionControlService versionControlService;

    /**
     * Internal endpoint to create initial version 0 for a newly created document
     * @param request Map containing documentId, content, and createdBy
     * @return VersionDTO or error
     */
    @PostMapping("/initial")
    public ResponseEntity<?> createInitialVersion(@RequestBody Map<String, Object> request) {
        try {
            Long documentId = Long.parseLong(request.get("documentId").toString());
            String content = request.get("content") != null ? request.get("content").toString() : "";
            Long createdBy = Long.parseLong(request.get("createdBy").toString());
            
            logger.info("Create initial version 0 request: documentId={}, createdBy={}", documentId, createdBy);
            
            VersionDTO version = versionControlService.createInitialVersion(documentId, content, createdBy);
            
            logger.info("Initial version 0 created successfully: documentId={}, versionNumber={}, createdBy={}", 
                    documentId, version.getVersionNumber(), createdBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(version);
        } catch (Exception e) {
            logger.warn("Initial version creation failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Internal endpoint to delete all versions for a document
     * Used when a document is deleted
     * @param documentId Document ID
     * @return Success response
     */
    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<?> deleteAllVersionsForDocument(@PathVariable Long documentId) {
        try {
            logger.info("Delete all versions request: documentId={}", documentId);
            versionControlService.deleteAllVersionsForDocument(documentId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "All versions deleted successfully for document " + documentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting versions for document: {}", documentId, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete versions: " + e.getMessage());
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
        status.put("service", "version-control-service");
        return ResponseEntity.ok(status);
    }
}

