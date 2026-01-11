package com.collaborative.editing.document.controller;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.model.Document;
import com.collaborative.editing.document.repository.DocumentRepository;
import com.collaborative.editing.document.service.DocumentService;
import com.collaborative.editing.document.service.FileProcessingService;
import com.collaborative.editing.document.client.UserServiceClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/documents")
@SuppressWarnings("null")
public class DocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private FileProcessingService fileProcessingService;
    
    @Autowired
    private UserServiceClient userServiceClient;

    @PostMapping
    public ResponseEntity<?> createDocument(@Valid @RequestBody DocumentDTO documentDTO) {
        logger.info("Create document request: title={}, ownerId={}", documentDTO.getTitle(), documentDTO.getOwnerId());
        try {
            DocumentDTO createdDocument = documentService.createDocument(documentDTO);
            logger.info("Document created successfully: ID={}, title={}, ownerId={}", 
                    createdDocument.getId(), createdDocument.getTitle(), createdDocument.getOwnerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDocument);
        } catch (RuntimeException e) {
            logger.warn("Document creation failed: title={}, ownerId={} - {}", 
                    documentDTO.getTitle(), documentDTO.getOwnerId(), e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @PutMapping("/{id}/edit")
    public ResponseEntity<?> editDocument(@PathVariable Long id, 
                                         @RequestParam Long userId,
                                         @RequestBody Map<String, String> editRequest) {
        logger.info("Edit document request: documentId={}, userId={}", id, userId);
        try {
            String newContent = editRequest.get("content");
            if (newContent == null) {
                logger.warn("Edit document failed: Content is required - documentId={}, userId={}", id, userId);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Content is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            DocumentDTO updatedDocument = documentService.editDocument(id, userId, newContent);
            logger.info("Document edited successfully: ID={}, userId={}, contentLength={}", 
                    id, userId, newContent.length());
            return ResponseEntity.ok(updatedDocument);
        } catch (RuntimeException e) {
            logger.warn("Document edit failed: documentId={}, userId={} - {}", id, userId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @PostMapping("/{id}/collaborators")
    public ResponseEntity<?> addCollaborator(@PathVariable Long id,
                                            @RequestParam Long ownerId,
                                            @RequestBody Map<String, Long> request) {
        try {
            Long collaboratorId = request.get("collaboratorId");
            if (collaboratorId == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Collaborator ID is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            DocumentDTO updatedDocument = documentService.addCollaborator(id, ownerId, collaboratorId);
            return ResponseEntity.ok(updatedDocument);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<?> removeCollaborator(@PathVariable Long id,
                                              @RequestParam Long ownerId,
                                              @PathVariable Long collaboratorId) {
        try {
            DocumentDTO updatedDocument = documentService.removeCollaborator(id, ownerId, collaboratorId);
            return ResponseEntity.ok(updatedDocument);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping("/{id}/changes")
    public ResponseEntity<List<ChangeTrackingDTO>> getRealTimeChanges(@PathVariable Long id) {
        List<ChangeTrackingDTO> changes = documentService.getRealTimeChanges(id);
        return ResponseEntity.ok(changes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id,
                                             @RequestParam(required = false) Long userId) {
        logger.debug("Get document request: ID={}, userId={}", id, userId);
        
        // Require userId for access control
        if (userId == null) {
            logger.warn("Get document request missing userId: ID={}", id);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User ID is required to access documents");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        
        try {
            DocumentDTO document = documentService.getDocumentByIdWithAccessCheck(id, userId);
            logger.debug("Document retrieved: ID={}, title={}, userId={}", id, document.getTitle(), userId);
            return ResponseEntity.ok(document);
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Access forbidden")) {
                logger.warn("Access denied: userId={} attempted to access documentId={}", userId, id);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Access forbidden: You do not have permission to access this document");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            } else {
                logger.warn("Document not found: ID={} - {}", id, errorMessage);
                Map<String, String> error = new HashMap<>();
                error.put("error", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
        }
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<DocumentDTO>> getDocumentsByOwner(@PathVariable Long ownerId) {
        List<DocumentDTO> documents = documentService.getDocumentsByOwner(ownerId);
        return ResponseEntity.ok(documents);
    }

    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getAllDocuments(@RequestParam(required = false) Long userId,
                                                             @RequestParam(required = false) Long adminId) {
        // If adminId is provided, check if user is admin and return all documents
        if (adminId != null) {
            try {
                if (!userServiceClient.isAdmin(adminId)) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Only admins can access all documents");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
                }
                logger.debug("Admin get all documents request: adminId={}", adminId);
                List<DocumentDTO> documents = documentService.getAllDocuments();
                logger.debug("Retrieved {} documents for admin", documents.size());
                return ResponseEntity.ok(documents);
            } catch (Exception e) {
                logger.error("Error checking admin status: {}", e.getMessage());
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to verify admin status");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        }
        
        if (userId != null) {
            logger.debug("Get accessible documents request: userId={}", userId);
            List<DocumentDTO> documents = documentService.getDocumentsAccessibleByUser(userId);
            logger.debug("Retrieved {} accessible documents for userId={}", documents.size(), userId);
            return ResponseEntity.ok(documents);
        }
        logger.debug("Get all documents request");
        List<DocumentDTO> documents = documentService.getAllDocuments();
        logger.debug("Retrieved {} documents", documents.size());
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocumentDTO>> searchDocuments(
            @RequestParam Long userId,
            @RequestParam String query) {
        try {
            logger.debug("Semantic search request: userId={}, query={}", userId, query);
            if (query == null || query.trim().isEmpty()) {
                // If query is empty, return all accessible documents
                List<DocumentDTO> documents = documentService.getDocumentsAccessibleByUser(userId);
                return ResponseEntity.ok(documents);
            }
            List<DocumentDTO> documents = documentService.searchDocuments(userId, query.trim());
            logger.debug("Search returned {} documents for userId={}, query={}", documents.size(), userId, query);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Error performing semantic search: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to perform search: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id, @RequestParam Long userId) {
        try {
            // Check if user is owner or collaborator to determine the action
            Document document = documentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            
            boolean isOwner = document.getOwnerId() != null && document.getOwnerId().equals(userId);
            boolean isCollaborator = document.getCollaboratorIds().contains(userId);
            
            documentService.deleteDocument(id, userId);
            
            Map<String, String> response = new HashMap<>();
            if (isOwner) {
                response.put("message", "Document deleted successfully");
            } else if (isCollaborator) {
                response.put("message", "You have left the document");
            } else {
                response.put("message", "Action completed successfully");
            }
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerId") Long ownerId,
            @RequestParam(value = "title", required = false) String title) {
        try {
            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid file name");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Check file extension
            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (!fileExtension.equals("docx") && !fileExtension.equals("txt")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Only .docx and .txt files are allowed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            // Extract content from file
            String content = fileProcessingService.extractContentFromFile(file, fileExtension);
            
            // Use provided title or file name without extension
            String documentTitle = title != null && !title.trim().isEmpty() 
                    ? title 
                    : fileName.substring(0, fileName.lastIndexOf("."));
            
            // Create document
            DocumentDTO documentDTO = new DocumentDTO();
            documentDTO.setTitle(documentTitle);
            documentDTO.setContent(content);
            documentDTO.setOwnerId(ownerId);
            
            DocumentDTO createdDocument = documentService.createDocument(documentDTO);
            logger.info("Document uploaded and created: ID={}, title={}, ownerId={}", 
                    createdDocument.getId(), createdDocument.getTitle(), ownerId);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDocument);
        } catch (Exception e) {
            logger.error("Error uploading document: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to upload document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @GetMapping("/{id}/export")
    public ResponseEntity<?> exportDocument(
            @PathVariable Long id,
            @RequestParam("format") String format,
            @RequestParam("userId") Long userId) {
        try {
            DocumentDTO document = documentService.getDocumentById(id);
            
            // Check if user has access
            if (!document.getOwnerId().equals(userId) && 
                (document.getCollaboratorIds() == null || !document.getCollaboratorIds().contains(userId))) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You do not have permission to export this document");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            if (!format.equalsIgnoreCase("docx") && !format.equalsIgnoreCase("txt")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid format. Only 'docx' and 'txt' are supported");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            byte[] fileBytes;
            String contentType;
            String fileExtension;
            
            if (format.equalsIgnoreCase("docx")) {
                fileBytes = fileProcessingService.createWordDocument(document.getTitle(), document.getContent());
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                fileExtension = "docx";
            } else {
                fileBytes = document.getContent().getBytes(StandardCharsets.UTF_8);
                contentType = "text/plain";
                fileExtension = "txt";
            }
            
            ByteArrayResource resource = new ByteArrayResource(fileBytes);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + document.getTitle() + "." + fileExtension + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            
            logger.info("Document exported: ID={}, format={}, userId={}", id, format, userId);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileBytes.length)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error exporting document: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to export document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

