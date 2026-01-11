package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.DocumentDTO;
import com.collaborative.editing.document.model.ChangeTracking;
import com.collaborative.editing.document.model.Document;
import com.collaborative.editing.document.repository.ChangeTrackingRepository;
import com.collaborative.editing.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.regex.Pattern;

@Service
@Transactional
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ChangeTrackingRepository changeTrackingRepository;
    
    @Autowired
    private com.collaborative.editing.document.client.VersionServiceClient versionServiceClient;
    
    @Autowired
    private com.collaborative.editing.document.client.UserServiceClient userServiceClient;

    public DocumentDTO createDocument(DocumentDTO documentDTO) {
        logger.debug("Creating document: title={}, ownerId={}", documentDTO.getTitle(), documentDTO.getOwnerId());
        Document document = new Document();
        document.setTitle(documentDTO.getTitle());
        document.setContent(documentDTO.getContent() != null ? documentDTO.getContent() : "");
        document.setOwnerId(documentDTO.getOwnerId());
        if (documentDTO.getCollaboratorIds() != null) {
            document.setCollaboratorIds(documentDTO.getCollaboratorIds());
            logger.debug("Document created with {} collaborators", documentDTO.getCollaboratorIds().size());
        }
        
        Document savedDocument = documentRepository.save(document);
        logger.info("Document created: ID={}, title={}, ownerId={}", 
                savedDocument.getId(), savedDocument.getTitle(), savedDocument.getOwnerId());
        
        // Create initial version 0 for the new document
        try {
            boolean versionCreated = versionServiceClient.createInitialVersion(
                    savedDocument.getId(), 
                    savedDocument.getContent(), 
                    savedDocument.getOwnerId()
            );
            if (versionCreated) {
                logger.info("Initial version 0 created for document: ID={}", savedDocument.getId());
            } else {
                logger.warn("Failed to create initial version 0 for document: ID={}", savedDocument.getId());
            }
        } catch (Exception e) {
            logger.error("Error creating initial version 0 for document: ID={}", savedDocument.getId(), e);
            // Don't fail document creation if version creation fails
        }
        
        return convertToDTO(savedDocument);
    }

    /**
     * Check if a user has permission to view a document (is owner or collaborator).
     */
    public boolean canUserAccessDocument(@NonNull Long documentId, @NonNull Long userId) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));
            
            // Check if document is active
            if (!"ACTIVE".equals(document.getStatus())) {
                logger.warn("Document is not active: documentId={}, status={}", documentId, document.getStatus());
                return false;
            }
            
            Long ownerId = document.getOwnerId();
            if (ownerId == null) {
                return false;
            }
            
            // User is owner or collaborator
            return ownerId.equals(userId) || document.getCollaboratorIds().contains(userId);
        } catch (Exception e) {
            logger.warn("Error checking access permission: documentId={}, userId={}, error={}", 
                    documentId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a user has permission to edit a document (is owner or collaborator).
     */
    public boolean canUserEditDocument(@NonNull Long documentId, @NonNull Long userId) {
        // Edit permission is the same as access permission
        return canUserAccessDocument(documentId, userId);
    }

    public DocumentDTO editDocument(@NonNull Long documentId, @NonNull Long userId, @NonNull String newContent) {
        logger.debug("Editing document: documentId={}, userId={}, contentLength={}", 
                documentId, userId, newContent.length());
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    logger.warn("Document not found: ID={}", documentId);
                    return new RuntimeException("Document not found");
                });
        
        // Check if user has permission (owner or collaborator)
        if (!canUserEditDocument(documentId, userId)) {
            logger.warn("Permission denied: userId={} does not have permission to edit documentId={}", 
                    userId, documentId);
            throw new RuntimeException("User does not have permission to edit this document");
        }
        
        document.setContent(newContent);
        document.setUpdatedAt(LocalDateTime.now());
        
        Document updatedDocument = documentRepository.save(document);
        
        // Track the change
        trackChange(documentId, userId, "UPDATE", newContent, null);
        
        logger.info("Document edited successfully: ID={}, userId={}, contentLength={}", 
                documentId, userId, newContent.length());
        return convertToDTO(updatedDocument);
    }

    public DocumentDTO addCollaborator(@NonNull Long documentId, @NonNull Long ownerId, @NonNull Long collaboratorId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->new RuntimeException("Document not found"));
        
        Long docOwnerId = document.getOwnerId();
        if (docOwnerId == null || !docOwnerId.equals(ownerId)) {
            throw new RuntimeException("Only the owner can add collaborators");
        }
        
        // Prevent users from adding themselves as collaborators
        if (ownerId.equals(collaboratorId)) {
            logger.warn("Attempt to add owner as collaborator: documentId={}, ownerId={}, collaboratorId={}", 
                    documentId, ownerId, collaboratorId);
            throw new RuntimeException("You cannot add yourself as a collaborator");
        }
        
        // Verify that the collaborator user exists
        if (!userServiceClient.userExists(collaboratorId)) {
            logger.warn("Attempt to add non-existent user as collaborator: documentId={}, collaboratorId={}", 
                    documentId, collaboratorId);
            throw new RuntimeException("User with ID " + collaboratorId + " does not exist or is not active");
        }
        
        // Check if user is already a collaborator
        if (document.getCollaboratorIds().contains(collaboratorId)) {
            throw new RuntimeException("User is already a collaborator on this document");
        }
        
        document.getCollaboratorIds().add(collaboratorId);
        document.setUpdatedAt(LocalDateTime.now());
        
        Document updatedDocument = documentRepository.save(document);
        logger.info("Collaborator added: documentId={}, collaboratorId={}", documentId, collaboratorId);
        return convertToDTO(updatedDocument);
    }

    public DocumentDTO removeCollaborator(@NonNull Long documentId, @NonNull Long ownerId, @NonNull Long collaboratorId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        Long docOwnerId = document.getOwnerId();
        if (docOwnerId == null || !docOwnerId.equals(ownerId)) {
            throw new RuntimeException("Only the owner can remove collaborators");
        }
        
        document.getCollaboratorIds().remove(collaboratorId);
        document.setUpdatedAt(LocalDateTime.now());
        
        Document updatedDocument = documentRepository.save(document);
        return convertToDTO(updatedDocument);
    }

    public List<ChangeTrackingDTO> getRealTimeChanges(Long documentId) {
        List<ChangeTracking> changes = changeTrackingRepository
                .findByDocumentIdOrderByTimestampDesc(documentId);
        return changes.stream()
                .map(this::convertChangeToDTO)
                .collect(Collectors.toList());
    }

    public List<ChangeTrackingDTO> getUnversionedChanges(Long documentId) {
        List<ChangeTracking> changes = changeTrackingRepository
                .findByDocumentIdAndVersionIdIsNullOrderByTimestampAsc(documentId);
        return changes.stream()
                .map(this::convertChangeToDTO)
                .collect(Collectors.toList());
    }

    public List<ChangeTrackingDTO> getChangesByVersionId(Long versionId) {
        List<ChangeTracking> changes = changeTrackingRepository
                .findByVersionIdOrderByTimestampAsc(versionId);
        return changes.stream()
                .map(this::convertChangeToDTO)
                .collect(Collectors.toList());
    }

    public void linkChangesToVersion(Long documentId, Long versionId) {
        List<ChangeTracking> unversionedChanges = changeTrackingRepository
                .findByDocumentIdAndVersionIdIsNullOrderByTimestampAsc(documentId);
        for (ChangeTracking change : unversionedChanges) {
            change.setVersionId(versionId);
            changeTrackingRepository.save(change);
        }
        logger.info("Linked {} changes to version {} for document {}", 
                unversionedChanges.size(), versionId, documentId);
    }

    public void unlinkChangesFromVersions(Long documentId, List<Long> versionIds) {
        for (Long versionId : versionIds) {
            List<ChangeTracking> changes = changeTrackingRepository
                    .findByVersionIdOrderByTimestampAsc(versionId);
            for (ChangeTracking change : changes) {
                if (change.getDocumentId().equals(documentId)) {
                    change.setVersionId(null);
                    changeTrackingRepository.save(change);
                }
            }
        }
        logger.info("Unlinked changes from {} versions for document {}", versionIds.size(), documentId);
    }

    public ChangeTrackingDTO trackChange(Long documentId, Long userId, String changeType, 
                                         String content, Integer position) {
        ChangeTracking change = new ChangeTracking(documentId, userId, changeType, content, position);
        ChangeTracking savedChange = changeTrackingRepository.save(change);
        return convertChangeToDTO(savedChange);
    }

    public DocumentDTO getDocumentById(@NonNull Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        return convertToDTO(document);
    }

    /**
     * Get document by ID with access control check.
     * Throws RuntimeException if user doesn't have access.
     */
    public DocumentDTO getDocumentByIdWithAccessCheck(@NonNull Long id, @NonNull Long userId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // Check if user has access
        if (!canUserAccessDocument(id, userId)) {
            logger.warn("Access denied: userId={} attempted to access documentId={}", userId, id);
            throw new RuntimeException("Access forbidden: You do not have permission to access this document");
        }
        
        return convertToDTO(document);
    }

    public List<DocumentDTO> getDocumentsByOwner(Long ownerId) {
        return documentRepository.findByOwnerId(ownerId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAll().stream()
                .filter(doc -> !"DELETED".equals(doc.getStatus()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DocumentDTO> getDocumentsAccessibleByUser(@NonNull Long userId) {
        // Get documents where user is owner
        List<Document> ownedDocuments = documentRepository.findByOwnerId(userId);
        
        // Get documents where user is collaborator
        List<Document> collaboratedDocuments = documentRepository.findDocumentsByCollaboratorId(userId);
        
        // Combine and deduplicate
        Set<Long> seenIds = new HashSet<>();
        List<Document> allDocuments = new ArrayList<>();
        
        for (Document doc : ownedDocuments) {
            if ("ACTIVE".equals(doc.getStatus()) && !seenIds.contains(doc.getId())) {
                allDocuments.add(doc);
                seenIds.add(doc.getId());
            }
        }
        
        for (Document doc : collaboratedDocuments) {
            // Only show ACTIVE documents to collaborators (not DELETED ones)
            if ("ACTIVE".equals(doc.getStatus()) && !seenIds.contains(doc.getId())) {
                allDocuments.add(doc);
                seenIds.add(doc.getId());
            }
        }
        
        return allDocuments.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Semantic search for documents accessible by a user.
     * Uses keyword matching, content similarity, and title matching to find relevant documents.
     */
    public List<DocumentDTO> searchDocuments(@NonNull Long userId, @NonNull String query) {
        logger.debug("Semantic search request: userId={}, query={}", userId, query);
        
        // Get all accessible documents
        List<Document> accessibleDocuments = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        
        // Get owned documents
        List<Document> ownedDocuments = documentRepository.findByOwnerId(userId);
        for (Document doc : ownedDocuments) {
            if ("ACTIVE".equals(doc.getStatus()) && !seenIds.contains(doc.getId())) {
                accessibleDocuments.add(doc);
                seenIds.add(doc.getId());
            }
        }
        
        // Get collaborated documents
        List<Document> collaboratedDocuments = documentRepository.findDocumentsByCollaboratorId(userId);
        for (Document doc : collaboratedDocuments) {
            if ("ACTIVE".equals(doc.getStatus()) && !seenIds.contains(doc.getId())) {
                accessibleDocuments.add(doc);
                seenIds.add(doc.getId());
            }
        }
        
        // Normalize query
        String normalizedQuery = query.toLowerCase().trim();
        if (normalizedQuery.isEmpty()) {
            return accessibleDocuments.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        
        // Score and rank documents
        List<ScoredDocument> scoredDocuments = new ArrayList<>();
        for (Document doc : accessibleDocuments) {
            double score = calculateRelevanceScore(doc, normalizedQuery);
            if (score > 0) {
                scoredDocuments.add(new ScoredDocument(doc, score));
            }
        }
        
        // Sort by score (descending) and return top results
        return scoredDocuments.stream()
                .sorted(Comparator.comparing(ScoredDocument::getScore).reversed())
                .limit(50) // Limit to top 50 results
                .map(sd -> convertToDTO(sd.getDocument()))
                .collect(Collectors.toList());
    }
    
    /**
     * Calculate relevance score for a document based on the search query.
     * Uses multiple factors:
     * - Title matching (highest weight)
     * - Content keyword matching
     * - Content similarity
     */
    private double calculateRelevanceScore(Document doc, String query) {
        double score = 0.0;
        
        String title = doc.getTitle() != null ? doc.getTitle().toLowerCase() : "";
        String content = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
        
        // Split query into words
        String[] queryWords = query.split("\\s+");
        
        // Title matching (highest weight)
        for (String word : queryWords) {
            if (title.contains(word)) {
                score += 10.0; // High weight for title matches
            }
            // Partial word match in title
            if (title.matches(".*\\b" + Pattern.quote(word) + ".*")) {
                score += 5.0;
            }
        }
        
        // Exact phrase match in title (very high weight)
        if (title.contains(query)) {
            score += 20.0;
        }
        
        // Content keyword matching
        int contentMatches = 0;
        for (String word : queryWords) {
            if (content.contains(word)) {
                contentMatches++;
                score += 1.0; // Lower weight for content matches
            }
        }
        
        // Bonus for multiple keyword matches in content
        if (contentMatches == queryWords.length) {
            score += 5.0; // All keywords found
        }
        
        // Exact phrase match in content (high weight)
        if (content.contains(query)) {
            score += 10.0;
        }
        
        // Calculate word frequency score
        int totalOccurrences = 0;
        for (String word : queryWords) {
            int count = countOccurrences(content, word);
            totalOccurrences += count;
        }
        score += Math.min(totalOccurrences * 0.5, 10.0); // Cap at 10
        
        // Proximity bonus: if multiple query words appear close together
        if (queryWords.length > 1) {
            score += calculateProximityBonus(content, queryWords);
        }
        
        return score;
    }
    
    private int countOccurrences(String text, String word) {
        if (text == null || word == null || text.isEmpty() || word.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
    
    private double calculateProximityBonus(String content, String[] queryWords) {
        if (content == null || content.isEmpty() || queryWords.length < 2) {
            return 0.0;
        }
        
        // Check if words appear within 50 characters of each other
        double bonus = 0.0;
        for (int i = 0; i < queryWords.length - 1; i++) {
            String word1 = queryWords[i];
            String word2 = queryWords[i + 1];
            int pos1 = content.indexOf(word1);
            int pos2 = content.indexOf(word2);
            
            if (pos1 != -1 && pos2 != -1) {
                int distance = Math.abs(pos2 - pos1);
                if (distance < 50) {
                    bonus += 3.0; // Words are close together
                } else if (distance < 200) {
                    bonus += 1.0; // Words are moderately close
                }
            }
        }
        return bonus;
    }
    
    // Helper class for scoring
    private static class ScoredDocument {
        private final Document document;
        private final double score;
        
        public ScoredDocument(Document document, double score) {
            this.document = document;
            this.score = score;
        }
        
        public Document getDocument() {
            return document;
        }
        
        public double getScore() {
            return score;
        }
    }

    public void deleteDocument(@NonNull Long documentId, @NonNull Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        Long docOwnerId = document.getOwnerId();
        
        // If user is the owner, actually delete the document and all its versions
        if (docOwnerId != null && docOwnerId.equals(userId)) {
            // Delete all versions for this document
            try {
                boolean versionsDeleted = versionServiceClient.deleteAllVersionsForDocument(documentId);
                if (versionsDeleted) {
                    logger.info("All versions deleted for document: documentId={}", documentId);
                } else {
                    logger.warn("Failed to delete versions for document: documentId={}", documentId);
                }
            } catch (Exception e) {
                logger.error("Error deleting versions for document: documentId={}", documentId, e);
                // Continue with document deletion even if version deletion fails
            }
            
            // Delete all change tracking records for this document
            try {
                List<ChangeTracking> changes = changeTrackingRepository.findByDocumentIdOrderByTimestampDesc(documentId);
                if (!changes.isEmpty()) {
                    changeTrackingRepository.deleteAll(changes);
                    logger.info("Deleted {} change tracking records for document: documentId={}", changes.size(), documentId);
                }
            } catch (Exception e) {
                logger.error("Error deleting change tracking records for document: documentId={}", documentId, e);
                // Continue with document deletion even if change tracking deletion fails
            }
            
            // Actually delete the document record from the database
            documentRepository.delete(document);
            logger.info("Document deleted by owner: documentId={}, userId={}", documentId, userId);
        } 
        // If user is a collaborator (not owner), remove them from collaborators list
        else if (document.getCollaboratorIds().contains(userId)) {
            document.getCollaboratorIds().remove(userId);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
            logger.info("Collaborator removed from document: documentId={}, userId={}", documentId, userId);
        } 
        // User has no permission
        else {
            throw new RuntimeException("You do not have permission to delete or leave this document");
        }
    }

    private DocumentDTO convertToDTO(Document document) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setContent(document.getContent());
        dto.setOwnerId(document.getOwnerId());
        dto.setCollaboratorIds(document.getCollaboratorIds());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        dto.setStatus(document.getStatus());
        return dto;
    }

    private ChangeTrackingDTO convertChangeToDTO(ChangeTracking change) {
        ChangeTrackingDTO dto = new ChangeTrackingDTO();
        dto.setId(change.getId());
        dto.setDocumentId(change.getDocumentId());
        dto.setUserId(change.getUserId());
        dto.setChangeType(change.getChangeType());
        dto.setContent(change.getContent());
        dto.setPosition(change.getPosition());
        dto.setTimestamp(change.getTimestamp());
        // Note: versionId is not exposed in DTO to keep it internal
        return dto;
    }
}

