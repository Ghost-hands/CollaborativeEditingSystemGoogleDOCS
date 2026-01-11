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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/versions")
public class VersionControlController {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionControlController.class);
    
    @Autowired
    private VersionControlService versionControlService;

    @PostMapping
    public ResponseEntity<?> createVersion(@RequestBody Map<String, Object> request) {
        try {
            Long documentId = Long.parseLong(request.get("documentId").toString());
            String content = request.get("content").toString();
            Long createdBy = Long.parseLong(request.get("createdBy").toString());
            String changeDescription = request.get("changeDescription") != null ? 
                    request.get("changeDescription").toString() : null;
            
            logger.info("Create version request: documentId={}, createdBy={}, description={}", 
                    documentId, createdBy, changeDescription);
            
            VersionDTO version = versionControlService.createVersion(
                    documentId, content, createdBy, changeDescription);
            
            logger.info("Version created successfully: documentId={}, versionNumber={}, createdBy={}", 
                    documentId, version.getVersionNumber(), createdBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(version);
        } catch (Exception e) {
            logger.warn("Version creation failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping("/document/{documentId}/history")
    public ResponseEntity<List<VersionDTO>> getVersionHistory(@PathVariable Long documentId) {
        logger.debug("Get version history request: documentId={}", documentId);
        List<VersionDTO> versions = versionControlService.getVersionHistory(documentId);
        logger.debug("Retrieved {} versions for documentId={}", versions.size(), documentId);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/document/{documentId}/version/{versionNumber}")
    public ResponseEntity<?> getVersionByNumber(@PathVariable Long documentId,
                                                @PathVariable Long versionNumber) {
        try {
            VersionDTO version = versionControlService.getVersionByNumber(documentId, versionNumber);
            return ResponseEntity.ok(version);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @PostMapping("/document/{documentId}/revert/{versionNumber}")
    public ResponseEntity<?> revertToVersion(@PathVariable Long documentId,
                                             @PathVariable Long versionNumber,
                                             @RequestParam Long userId) {
        try {
            VersionDTO revertedVersion = versionControlService.revertToVersion(
                    documentId, versionNumber, userId);
            return ResponseEntity.ok(revertedVersion);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping("/document/{documentId}/contributions")
    public ResponseEntity<Map<String, Object>> getUserContributions(@PathVariable Long documentId) {
        Map<String, Object> contributions = versionControlService.getUserContributions(documentId);
        return ResponseEntity.ok(contributions);
    }

    @GetMapping("/document/{documentId}/user/{userId}/contribution")
    public ResponseEntity<Map<String, Object>> getUserContributionForDocument(
            @PathVariable Long documentId,
            @PathVariable Long userId) {
        Map<String, Object> contribution = versionControlService
                .getUserContributionForDocument(documentId, userId);
        return ResponseEntity.ok(contribution);
    }

    @GetMapping("/user/{userId}/contributions")
    public ResponseEntity<List<Map<String, Object>>> getAllContributionsByUser(
            @PathVariable Long userId) {
        List<Map<String, Object>> contributions = versionControlService
                .getAllContributionsByUser(userId);
        return ResponseEntity.ok(contributions);
    }

    @GetMapping("/version/{versionId}/changes")
    public ResponseEntity<List<com.collaborative.editing.common.dto.ChangeTrackingDTO>> getChangesForVersion(
            @PathVariable Long versionId) {
        List<com.collaborative.editing.common.dto.ChangeTrackingDTO> changes = 
                versionControlService.getChangesForVersion(versionId);
        return ResponseEntity.ok(changes);
    }

    @GetMapping("/document/{documentId}/history/with-diffs")
    public ResponseEntity<List<Map<String, Object>>> getVersionHistoryWithDiffs(@PathVariable Long documentId) {
        List<Map<String, Object>> history = versionControlService.getVersionHistoryWithDiffs(documentId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/document/{documentId}/version/{versionNumber}/diff")
    public ResponseEntity<?> getVersionDiff(@PathVariable Long documentId,
                                           @PathVariable Long versionNumber) {
        try {
            Map<String, Object> diff = versionControlService.getVersionDiff(documentId, versionNumber);
            return ResponseEntity.ok(diff);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping("/document/{documentId}/diff")
    public ResponseEntity<?> getVersionDiffBetween(@PathVariable Long documentId,
                                                    @RequestParam(required = false) Long from,
                                                    @RequestParam Long to) {
        try {
            Map<String, Object> diff = versionControlService.getVersionDiff(documentId, from, to);
            return ResponseEntity.ok(diff);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}

