package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.CursorPositionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track cursor positions for users editing documents.
 * Each user gets a unique color for their cursor.
 */
@Service
public class CursorTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(CursorTrackingService.class);
    
    // Map of documentId -> Map of userId -> CursorPositionDTO
    private final Map<Long, Map<Long, CursorPositionDTO>> documentCursors = new ConcurrentHashMap<>();
    
    // Map of userId -> color (for consistent color assignment)
    private final Map<Long, String> userColors = new ConcurrentHashMap<>();
    
    // Predefined color palette for cursors
    private static final String[] CURSOR_COLORS = {
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
        "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B739", "#52BE80",
        "#EC7063", "#5DADE2", "#58D68D", "#F4D03F", "#AF7AC5"
    };
    
    /**
     * Update cursor position for a user in a document.
     * Assigns a color to the user if they don't have one yet.
     */
    public void updateCursor(Long documentId, Long userId, Integer position, String userName) {
        if (documentId == null || userId == null) {
            logger.warn("Cannot update cursor: documentId or userId is null");
            return;
        }
        
        // Get or assign color for this user
        String color = userColors.computeIfAbsent(userId, this::assignColor);
        
        // Create or update cursor position
        CursorPositionDTO cursor = new CursorPositionDTO(userId, documentId, position, userName, color);
        
        // Store cursor for this document
        documentCursors.computeIfAbsent(documentId, k -> new ConcurrentHashMap<>())
                .put(userId, cursor);
        
        logger.debug("Updated cursor position: documentId={}, userId={}, position={}, color={}", 
                documentId, userId, position, color);
    }
    
    /**
     * Get all cursor positions for a document.
     */
    public List<CursorPositionDTO> getCursorsForDocument(Long documentId) {
        if (documentId == null) {
            return new ArrayList<>();
        }
        
        Map<Long, CursorPositionDTO> cursors = documentCursors.get(documentId);
        if (cursors == null || cursors.isEmpty()) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(cursors.values());
    }
    
    /**
     * Remove cursor for a user in a document (when user leaves).
     */
    public void removeCursor(Long documentId, Long userId) {
        if (documentId == null || userId == null) {
            return;
        }
        
        Map<Long, CursorPositionDTO> cursors = documentCursors.get(documentId);
        if (cursors != null) {
            cursors.remove(userId);
            logger.debug("Removed cursor: documentId={}, userId={}", documentId, userId);
            
            // Clean up empty document cursor map
            if (cursors.isEmpty()) {
                documentCursors.remove(documentId);
            }
        }
    }
    
    /**
     * Remove all cursors for a user across all documents (when user disconnects).
     */
    public void removeAllCursorsForUser(Long userId) {
        if (userId == null) {
            return;
        }
        
        // Remove user from all document cursor maps
        documentCursors.values().forEach(cursors -> cursors.remove(userId));
        
        // Clean up empty document cursor maps
        documentCursors.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        logger.debug("Removed all cursors for user: userId={}", userId);
    }
    
    /**
     * Assign a color to a user based on their userId.
     * Uses a hash-based approach to consistently assign colors.
     */
    private String assignColor(Long userId) {
        int colorIndex = (int) (userId % CURSOR_COLORS.length);
        return CURSOR_COLORS[Math.abs(colorIndex)];
    }
}

