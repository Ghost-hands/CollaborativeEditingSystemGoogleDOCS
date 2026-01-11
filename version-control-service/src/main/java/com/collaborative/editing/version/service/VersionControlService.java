package com.collaborative.editing.version.service;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.VersionDTO;
import com.collaborative.editing.version.client.DocumentServiceClient;
import com.collaborative.editing.version.model.DocumentVersion;
import com.collaborative.editing.version.model.UserContribution;
import com.collaborative.editing.version.repository.DocumentVersionRepository;
import com.collaborative.editing.version.repository.UserContributionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class VersionControlService {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionControlService.class);
    
    @Autowired
    private DocumentVersionRepository versionRepository;
    
    @Autowired
    private UserContributionRepository contributionRepository;
    
    @Autowired
    private DocumentServiceClient documentServiceClient;
    
    @Autowired
    private DiffService diffService;
    
    @Autowired
    private com.collaborative.editing.version.client.UserServiceClient userServiceClient;

    /**
     * Create initial version 0 for a newly created document
     */
    public VersionDTO createInitialVersion(Long documentId, String content, Long createdBy) {
        logger.debug("Creating initial version 0: documentId={}, createdBy={}", documentId, createdBy);
        
        // Check if version 0 already exists
        Optional<DocumentVersion> existingVersion0 = versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L);
        if (existingVersion0.isPresent()) {
            logger.warn("Version 0 already exists for documentId={}", documentId);
            return convertToDTO(existingVersion0.get());
        }
        
        // Create version 0
        DocumentVersion version = new DocumentVersion(documentId, 0L, content != null ? content : "", createdBy);
        version.setChangeDescription("Initial document creation");
        
        DocumentVersion savedVersion = versionRepository.save(version);
        
        // Update user contribution
        updateUserContribution(documentId, createdBy, content != null ? content : "");
        
        logger.info("Initial version 0 created: documentId={}, createdBy={}", documentId, createdBy);
        return convertToDTO(savedVersion);
    }

    public VersionDTO createVersion(Long documentId, String content, Long createdBy, String changeDescription) {
        logger.debug("Creating version: documentId={}, createdBy={}, contentLength={}", 
                documentId, createdBy, content != null ? content.length() : 0);
        
        // Get unversioned changes
        List<ChangeTrackingDTO> unversionedChanges = documentServiceClient.getUnversionedChanges(documentId);
        
        // Check if there are any unversioned changes
        if (unversionedChanges.isEmpty()) {
            // Check if content actually changed by comparing with the latest version
            Optional<DocumentVersion> latestVersionOpt = versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId);
            
            if (latestVersionOpt.isPresent()) {
                DocumentVersion latestVersion = latestVersionOpt.get();
                String latestContent = latestVersion.getContent();
                
                // Normalize content for comparison (trim whitespace differences)
                String normalizedNewContent = content != null ? content.trim() : "";
                String normalizedLatestContent = latestContent != null ? latestContent.trim() : "";
                
                // If content hasn't changed, throw an exception
                if (normalizedNewContent.equals(normalizedLatestContent)) {
                    logger.warn("Version creation skipped: No changes detected for documentId={}", documentId);
                    throw new RuntimeException("No changes detected. The document content is identical to the latest version.");
                }
            } else {
                // No previous version and no changes - this shouldn't happen, but handle gracefully
                logger.warn("Version creation skipped: No unversioned changes and no previous version for documentId={}", documentId);
                throw new RuntimeException("No changes to version.");
            }
        }
        
        // Get the next version number
        // If version 0 exists, count includes it, so we use count directly
        // Otherwise, we start from 1
        Long versionCount = versionRepository.countByDocumentId(documentId);
        Optional<DocumentVersion> version0 = versionRepository.findByDocumentIdAndVersionNumber(documentId, 0L);
        Long versionNumber;
        if (version0.isPresent()) {
            // Version 0 exists, so next version is count (since count includes version 0)
            versionNumber = versionCount;
        } else {
            // No version 0, start from 1
            versionNumber = versionCount + 1;
        }
        logger.debug("Next version number: {} for documentId={} (versionCount={})", versionNumber, documentId, versionCount);
        
        // Create version with content (stored for backward compatibility and performance)
        DocumentVersion version = new DocumentVersion(documentId, versionNumber, content, createdBy);
        if (changeDescription != null && !changeDescription.trim().isEmpty()) {
            version.setChangeDescription(changeDescription);
        } else {
            // Auto-generate change description based on number of changes
            int changeCount = unversionedChanges.size();
            version.setChangeDescription(changeCount > 0 ? 
                String.format("Document edited (%d change%s)", changeCount, changeCount > 1 ? "s" : "") : 
                "Document edited");
        }
        
        DocumentVersion savedVersion = versionRepository.save(version);
        
        // Link unversioned changes to this version
        documentServiceClient.linkChangesToVersion(documentId, savedVersion.getId());
        
        // Update user contribution
        updateUserContribution(documentId, createdBy, content);
        
        logger.info("Version created: documentId={}, versionNumber={}, changesLinked={}", 
                documentId, versionNumber, unversionedChanges.size());
        
        return convertToDTO(savedVersion);
    }

    public List<VersionDTO> getVersionHistory(Long documentId) {
        List<DocumentVersion> versions = versionRepository
                .findByDocumentIdOrderByVersionNumberDesc(documentId);
        return versions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public VersionDTO getVersionByNumber(Long documentId, Long versionNumber) {
        DocumentVersion version = versionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        return convertToDTO(version);
    }

    public VersionDTO revertToVersion(Long documentId, Long versionNumber, Long userId) {
        // Validate inputs
        if (documentId == null || versionNumber == null || userId == null) {
            throw new RuntimeException("Document ID, version number, and user ID are required");
        }
        
        DocumentVersion targetVersion = versionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version " + versionNumber + " not found for document " + documentId));
        
        logger.info("Reverting document {} to version {}: creating new version from restored content", 
                documentId, versionNumber);
        
        // Ensure content is not null
        String targetContent = targetVersion.getContent();
        if (targetContent == null) {
            targetContent = "";
            logger.warn("Target version content was null, using empty string");
        }
        
        // Update document content to target version content
        com.collaborative.editing.common.dto.DocumentDTO updatedDoc = documentServiceClient.updateDocumentContent(documentId, targetContent);
        if (updatedDoc == null) {
            throw new RuntimeException("Failed to update document content");
        }
        
        // Reset WebSocket state to ensure real-time editing works after revert
        // This clears the in-memory state so it will be reinitialized with the new content
        documentServiceClient.resetWebSocketState(documentId);
        logger.info("WebSocket state reset for documentId={} after revert", documentId);
        
        // Create a new version with the reverted content
        // All previous versions are preserved - we just create a new version from the restored content
        // Get the latest version number to ensure we don't create duplicates
        Optional<DocumentVersion> latestVersionOpt = versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId);
        Long newVersionNumber;
        if (latestVersionOpt.isPresent()) {
            // Get the next version number after the latest
            newVersionNumber = latestVersionOpt.get().getVersionNumber() + 1;
        } else {
            // No versions exist yet, start from 1 (version 0 should exist, but handle gracefully)
            newVersionNumber = 1L;
            logger.warn("No versions found for documentId={}, starting from version 1", documentId);
        }
        
        logger.debug("Creating revert version {} for documentId={} (target was version {})", 
                newVersionNumber, documentId, versionNumber);
        
        DocumentVersion revertedVersion = new DocumentVersion(
                documentId, 
                newVersionNumber, 
                targetContent, 
                userId
        );
        revertedVersion.setChangeDescription("Restored from version " + versionNumber + " (all versions preserved)");
        
        DocumentVersion savedVersion;
        try {
            savedVersion = versionRepository.save(revertedVersion);
        } catch (Exception e) {
            logger.error("Failed to save reverted version for documentId={}, versionNumber={}", 
                    documentId, newVersionNumber, e);
            throw new RuntimeException("Failed to create revert version: " + e.getMessage());
        }
        
        // Link any remaining unversioned changes to this revert version
        documentServiceClient.linkChangesToVersion(documentId, savedVersion.getId());
        
        // Update user contribution
        updateUserContribution(documentId, userId, targetContent);
        
        logger.info("Document {} restored from version {}: new version {} created (all versions preserved)", 
                documentId, versionNumber, newVersionNumber);
        
        return convertToDTO(savedVersion);
    }

    public Map<String, Object> getUserContributions(Long documentId) {
        List<UserContribution> contributions = contributionRepository.findByDocumentId(documentId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", documentId);
        result.put("totalContributors", contributions.size());
        
        List<Map<String, Object>> contributorStats = contributions.stream()
                .map(contrib -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("userId", contrib.getUserId());
                    stats.put("editCount", contrib.getEditCount());
                    stats.put("charactersAdded", contrib.getCharactersAdded());
                    stats.put("charactersDeleted", contrib.getCharactersDeleted());
                    stats.put("firstContribution", contrib.getFirstContribution());
                    stats.put("lastContribution", contrib.getLastContribution());
                    return stats;
                })
                .collect(Collectors.toList());
        
        result.put("contributors", contributorStats);
        return result;
    }

    public Map<String, Object> getUserContributionForDocument(Long documentId, Long userId) {
        UserContribution contribution = contributionRepository
                .findByDocumentIdAndUserId(documentId, userId)
                .orElse(null);
        
        Map<String, Object> result = new HashMap<>();
        if (contribution != null) {
            result.put("userId", contribution.getUserId());
            result.put("documentId", contribution.getDocumentId());
            result.put("editCount", contribution.getEditCount());
            result.put("charactersAdded", contribution.getCharactersAdded());
            result.put("charactersDeleted", contribution.getCharactersDeleted());
            result.put("firstContribution", contribution.getFirstContribution());
            result.put("lastContribution", contribution.getLastContribution());
        } else {
            result.put("message", "No contributions found for this user and document");
        }
        return result;
    }

    public List<Map<String, Object>> getAllContributionsByUser(Long userId) {
        List<UserContribution> contributions = contributionRepository.findByUserId(userId);
        return contributions.stream()
                .map(contrib -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("documentId", contrib.getDocumentId());
                    stats.put("editCount", contrib.getEditCount());
                    stats.put("charactersAdded", contrib.getCharactersAdded());
                    stats.put("charactersDeleted", contrib.getCharactersDeleted());
                    stats.put("firstContribution", contrib.getFirstContribution());
                    stats.put("lastContribution", contrib.getLastContribution());
                    return stats;
                })
                .collect(Collectors.toList());
    }

    private void updateUserContribution(Long documentId, Long userId, String content) {
        UserContribution contribution = contributionRepository
                .findByDocumentIdAndUserId(documentId, userId)
                .orElse(new UserContribution(documentId, userId));
        
        contribution.setEditCount(contribution.getEditCount() + 1);
        contribution.setCharactersAdded(contribution.getCharactersAdded() + content.length());
        contribution.setLastContribution(LocalDateTime.now());
        
        contributionRepository.save(contribution);
    }

    public List<com.collaborative.editing.common.dto.ChangeTrackingDTO> getChangesForVersion(Long versionId) {
        return documentServiceClient.getChangesByVersionId(versionId);
    }
    
    /**
     * Get diff between two versions (like GitHub)
     * Returns diff segments with proper additions and deletions
     * Includes user attribution for each change
     */
    public Map<String, Object> getVersionDiff(Long documentId, Long fromVersionNumber, Long toVersionNumber) {
        DocumentVersion fromVersion = null;
        if (fromVersionNumber != null) {
            fromVersion = versionRepository
                    .findByDocumentIdAndVersionNumber(documentId, fromVersionNumber)
                    .orElse(null);
        }
        
        DocumentVersion toVersion = versionRepository
                .findByDocumentIdAndVersionNumber(documentId, toVersionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        
        String oldContent = (fromVersion != null) ? (fromVersion.getContent() != null ? fromVersion.getContent() : "") : "";
        String newContent = (toVersion.getContent() != null) ? toVersion.getContent() : "";
        
        List<DiffService.DiffSegment> diffSegments = diffService.computeDiff(oldContent, newContent);
        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);
        
        // Get changes for this version to attribute edits to users
        List<com.collaborative.editing.common.dto.ChangeTrackingDTO> changes = new ArrayList<>();
        try {
            changes = documentServiceClient.getChangesByVersionId(toVersion.getId());
            logger.debug("Retrieved {} changes for version {} (versionId={})", 
                    changes.size(), toVersionNumber, toVersion.getId());
            // Log change details for debugging
            for (com.collaborative.editing.common.dto.ChangeTrackingDTO change : changes) {
                logger.debug("Change: type={}, userId={}, position={}, content={}", 
                        change.getChangeType(), 
                        change.getUserId(), 
                        change.getPosition(),
                        change.getContent() != null ? 
                            change.getContent().substring(0, Math.min(50, change.getContent().length())) : "null");
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch changes for version {}: {}", toVersion.getId(), e.getMessage());
        }
        
        // Create a map of user IDs to usernames
        Set<Long> userIds = new HashSet<>();
        userIds.add(toVersion.getCreatedBy()); // Version creator
        for (com.collaborative.editing.common.dto.ChangeTrackingDTO change : changes) {
            if (change.getUserId() != null) {
                userIds.add(change.getUserId());
            }
        }
        
        Map<Long, String> userIdToUsername = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<com.collaborative.editing.common.dto.UserDTO> users = userServiceClient.getUsersByIds(new ArrayList<>(userIds));
            for (com.collaborative.editing.common.dto.UserDTO user : users) {
                if (user != null && user.getId() != null) {
                    userIdToUsername.put(user.getId(), user.getUsername() != null ? user.getUsername() : "User " + user.getId());
                }
            }
        }
        
        // Default to version creator if no username found
        String defaultUsername = userIdToUsername.getOrDefault(toVersion.getCreatedBy(), 
                "User " + toVersion.getCreatedBy());
        
        // Convert segments to maps for JSON serialization with user attribution
        List<Map<String, Object>> segmentsList = new ArrayList<>();
        
        for (DiffService.DiffSegment segment : diffSegments) {
            Map<String, Object> segmentMap = new HashMap<>();
            segmentMap.put("type", segment.getType().toString());
            segmentMap.put("content", segment.getContent());
            segmentMap.put("startLine", segment.getStartLine());
            segmentMap.put("endLine", segment.getEndLine());
            
            // Attribute changes to users based on change tracking
            // Map operations to diff segments by position, not content matching
            // This ensures accurate attribution when users edit existing text
            if (segment.getType() == DiffService.DiffSegment.Type.ADDED || 
                segment.getType() == DiffService.DiffSegment.Type.REMOVED) {
                
                // Try to find the user who made this change by matching position
                Long attributedUserId = null;
                String attributedUsername = defaultUsername;
                
                // Match operations to segments using content matching
                // Since diff segments are line-based but operations are character-based,
                // we match by checking if operation content appears in the segment
                // Prefer exact matches over partial matches
                
                if (segment.getType() == DiffService.DiffSegment.Type.ADDED) {
                    // For additions, look for INSERT operations
                    // Try exact match first, then partial match
                    com.collaborative.editing.common.dto.ChangeTrackingDTO exactMatch = null;
                    com.collaborative.editing.common.dto.ChangeTrackingDTO partialMatch = null;
                    
                    for (com.collaborative.editing.common.dto.ChangeTrackingDTO change : changes) {
                        if ("INSERT".equals(change.getChangeType()) && 
                            change.getContent() != null && 
                            !change.getContent().isEmpty()) {
                            
                            String insertContent = change.getContent();
                            String segmentContent = segment.getContent();
                            
                            // Check for exact match (segment content equals operation content)
                            if (segmentContent.equals(insertContent)) {
                                exactMatch = change;
                                break; // Exact match found, use it
                            }
                            
                            // Check for partial match (operation content is contained in segment)
                            if (segmentContent.contains(insertContent)) {
                                if (partialMatch == null) {
                                    partialMatch = change;
                                }
                            }
                        }
                    }
                    
                    // Use exact match if available, otherwise use partial match
                    if (exactMatch != null) {
                        attributedUserId = exactMatch.getUserId();
                    } else if (partialMatch != null) {
                        attributedUserId = partialMatch.getUserId();
                    }
                } else {
                    // For deletions, look for DELETE operations
                    // Try exact match first, then partial match
                    com.collaborative.editing.common.dto.ChangeTrackingDTO exactMatch = null;
                    com.collaborative.editing.common.dto.ChangeTrackingDTO partialMatch = null;
                    
                    for (com.collaborative.editing.common.dto.ChangeTrackingDTO change : changes) {
                        if ("DELETE".equals(change.getChangeType()) && 
                            change.getContent() != null && 
                            !change.getContent().isEmpty()) {
                            
                            String deleteContent = change.getContent();
                            String segmentContent = segment.getContent();
                            
                            // Check for exact match
                            if (segmentContent.equals(deleteContent)) {
                                exactMatch = change;
                                break; // Exact match found, use it
                            }
                            
                            // Check for partial match
                            if (segmentContent.contains(deleteContent)) {
                                if (partialMatch == null) {
                                    partialMatch = change;
                                }
                            }
                        }
                    }
                    
                    // Use exact match if available, otherwise use partial match
                    if (exactMatch != null) {
                        attributedUserId = exactMatch.getUserId();
                    } else if (partialMatch != null) {
                        attributedUserId = partialMatch.getUserId();
                    }
                }
                
                // Log when we can't find a match for debugging
                if (attributedUserId == null && !changes.isEmpty()) {
                    logger.debug("No matching operation found for segment: type={}, content={}, segmentStartLine={}, changesCount={}", 
                            segment.getType(), 
                            segment.getContent().length() > 50 ? 
                                segment.getContent().substring(0, 50) + "..." : segment.getContent(),
                            segment.getStartLine(),
                            changes.size());
                } else if (attributedUserId != null) {
                    logger.debug("Attributed segment to user: userId={}, segmentType={}, segmentContent={}", 
                            attributedUserId,
                            segment.getType(),
                            segment.getContent().length() > 50 ? 
                                segment.getContent().substring(0, 50) + "..." : segment.getContent());
                }
                
                // Only attribute to version creator if we found a matching operation
                // Don't attribute UNCHANGED content or segments that don't match any operation
                // If no specific change found, don't attribute (or attribute to original creator from previous version)
                if (attributedUserId == null) {
                    // For ADDED segments with no matching operation, it might be unchanged content
                    // that the diff algorithm incorrectly marked as added. Don't attribute it.
                    // For REMOVED segments with no matching operation, same logic applies.
                    // We'll leave it null and let the frontend handle it (or use version creator as fallback)
                    attributedUserId = toVersion.getCreatedBy();
                }
                
                // Get username
                attributedUsername = userIdToUsername.getOrDefault(attributedUserId, 
                        "User " + attributedUserId);
                
                segmentMap.put("userId", attributedUserId);
                segmentMap.put("username", attributedUsername);
            } else if (segment.getType() == DiffService.DiffSegment.Type.UNCHANGED) {
                // UNCHANGED segments should not be attributed to the version creator
                // They represent content that existed in the previous version
                // Optionally, we could attribute them to the previous version's creator
                // For now, we'll leave them without attribution
                segmentMap.put("userId", null);
                segmentMap.put("username", null);
            }
            
            segmentsList.add(segmentMap);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("fromVersion", fromVersionNumber);
        result.put("toVersion", toVersionNumber);
        result.put("segments", segmentsList);
        result.put("stats", stats);
        result.put("fromContent", oldContent);
        result.put("toContent", newContent);
        
        return result;
    }
    
    /**
     * Get diff for a specific version (compared to previous version)
     * Includes user attribution for each change
     */
    public Map<String, Object> getVersionDiff(Long documentId, Long versionNumber) {
        // Verify version exists
        versionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        
        // Find previous version
        Long previousVersionNumber = null;
        if (versionNumber > 0) {
            previousVersionNumber = versionNumber - 1;
        } else {
            // For version 0, there's no previous version
            previousVersionNumber = null;
        }
        
        return getVersionDiff(documentId, previousVersionNumber, versionNumber);
    }
    
    /**
     * Get version history with proper diffs and usernames
     */
    public List<Map<String, Object>> getVersionHistoryWithDiffs(Long documentId) {
        List<DocumentVersion> versions = versionRepository
                .findByDocumentIdOrderByVersionNumberDesc(documentId);
        
        // Collect all user IDs
        Set<Long> userIds = new HashSet<>();
        for (DocumentVersion version : versions) {
            userIds.add(version.getCreatedBy());
        }
        
        // Fetch usernames
        Map<Long, String> usernameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<com.collaborative.editing.common.dto.UserDTO> users = userServiceClient.getUsersByIds(new ArrayList<>(userIds));
            for (com.collaborative.editing.common.dto.UserDTO user : users) {
                if (user != null && user.getId() != null) {
                    usernameMap.put(user.getId(), user.getUsername() != null ? user.getUsername() : "User " + user.getId());
                }
            }
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            DocumentVersion version = versions.get(i);
            Map<String, Object> versionMap = new HashMap<>();
            versionMap.put("id", version.getId());
            versionMap.put("documentId", version.getDocumentId());
            versionMap.put("versionNumber", version.getVersionNumber());
            versionMap.put("content", version.getContent());
            versionMap.put("createdBy", version.getCreatedBy());
            versionMap.put("createdByUsername", usernameMap.getOrDefault(version.getCreatedBy(), "User " + version.getCreatedBy()));
            versionMap.put("createdAt", version.getCreatedAt());
            versionMap.put("changeDescription", version.getChangeDescription());
            
            // Compute diff stats compared to previous version
            if (i < versions.size() - 1) {
                DocumentVersion previousVersion = versions.get(i + 1);
                String oldContent = previousVersion.getContent() != null ? previousVersion.getContent() : "";
                String newContent = version.getContent() != null ? version.getContent() : "";
                Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);
                versionMap.put("diffStats", stats);
            } else {
                // First version (latest) - no previous to compare
                Map<String, Object> stats = new HashMap<>();
                stats.put("addedLines", 0);
                stats.put("removedLines", 0);
                stats.put("addedChars", 0);
                stats.put("removedChars", 0);
                stats.put("netChange", 0);
                versionMap.put("diffStats", stats);
            }
            
            result.add(versionMap);
        }
        
        return result;
    }

    /**
     * Delete all versions for a document
     * Also deletes associated user contributions
     * @param documentId Document ID
     */
    public void deleteAllVersionsForDocument(Long documentId) {
        logger.info("Deleting all versions for document: documentId={}", documentId);
        
        // Delete all versions
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        if (!versions.isEmpty()) {
            versionRepository.deleteAll(versions);
            logger.info("Deleted {} versions for documentId={}", versions.size(), documentId);
        }
        
        // Delete all user contributions for this document
        List<UserContribution> contributions = contributionRepository.findByDocumentId(documentId);
        if (!contributions.isEmpty()) {
            contributionRepository.deleteAll(contributions);
            logger.info("Deleted {} user contributions for documentId={}", contributions.size(), documentId);
        }
        
        logger.info("Successfully deleted all versions and contributions for documentId={}", documentId);
    }

    private VersionDTO convertToDTO(DocumentVersion version) {
        VersionDTO dto = new VersionDTO();
        dto.setId(version.getId());
        dto.setDocumentId(version.getDocumentId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setContent(version.getContent());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setChangeDescription(version.getChangeDescription());
        return dto;
    }
}

