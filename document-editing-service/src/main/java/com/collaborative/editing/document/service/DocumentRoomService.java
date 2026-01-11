package com.collaborative.editing.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage WebSocket rooms for documents.
 * Each document is treated as a separate room, and only authorized users can join.
 */
@Service
public class DocumentRoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentRoomService.class);
    
    @Autowired
    private DocumentService documentService;
    
    // Map of documentId -> Set of userIds currently in the room
    private final Map<Long, Set<Long>> documentRooms = new ConcurrentHashMap<>();
    
    // Map of documentId -> Map of userId -> UserInfo (for tracking user details)
    private final Map<Long, Map<Long, UserInfo>> roomUsers = new ConcurrentHashMap<>();
    
    // Map of userId -> Set of documentIds (for tracking which documents a user is in)
    private final Map<Long, Set<Long>> userDocuments = new ConcurrentHashMap<>();
    
    /**
     * Check if a user has permission to join a document room (can edit the document).
     */
    public boolean canUserJoinRoom(@NonNull Long documentId, @NonNull Long userId) {
        try {
            // Check if user is owner or collaborator
            return documentService.canUserEditDocument(documentId, userId);
        } catch (Exception e) {
            logger.warn("Error checking user permission for document room: documentId={}, userId={}, error={}", 
                    documentId, userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a user to a document room.
     * Returns true if user was added, false if user already exists or doesn't have permission.
     */
    public boolean addUserToRoom(@NonNull Long documentId, @NonNull Long userId, @NonNull String userName) {
        if (!canUserJoinRoom(documentId, userId)) {
            logger.warn("User denied access to document room: documentId={}, userId={}", documentId, userId);
            return false;
        }
        
        documentRooms.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        roomUsers.computeIfAbsent(documentId, k -> new ConcurrentHashMap<>())
                .put(userId, new UserInfo(userId, userName, System.currentTimeMillis()));
        // Track which documents this user is in
        userDocuments.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(documentId);
        
        logger.info("User joined document room: documentId={}, userId={}, userName={}, totalUsers={}", 
                documentId, userId, userName, documentRooms.get(documentId).size());
        
        return true;
    }
    
    /**
     * Remove a user from a document room.
     */
    public void removeUserFromRoom(@NonNull Long documentId, @NonNull Long userId) {
        Set<Long> users = documentRooms.get(documentId);
        if (users != null) {
            users.remove(userId);
            logger.info("User left document room: documentId={}, userId={}, remainingUsers={}", 
                    documentId, userId, users.size());
            
            // Clean up empty rooms
            if (users.isEmpty()) {
                documentRooms.remove(documentId);
                roomUsers.remove(documentId);
                logger.debug("Removed empty document room: documentId={}", documentId);
            }
        }
        
        Map<Long, UserInfo> userMap = roomUsers.get(documentId);
        if (userMap != null) {
            userMap.remove(userId);
            if (userMap.isEmpty()) {
                roomUsers.remove(documentId);
            }
        }
        
        // Remove from user's document tracking
        Set<Long> userDocs = userDocuments.get(userId);
        if (userDocs != null) {
            userDocs.remove(documentId);
            if (userDocs.isEmpty()) {
                userDocuments.remove(userId);
            }
        }
    }
    
    /**
     * Remove a user from all document rooms (used on disconnect).
     */
    public void removeUserFromAllRooms(@NonNull Long userId) {
        Set<Long> userDocs = userDocuments.get(userId);
        if (userDocs != null) {
            // Create a copy to avoid concurrent modification issues
            Set<Long> documentsToLeave = new HashSet<>(userDocs);
            for (Long documentId : documentsToLeave) {
                removeUserFromRoom(documentId, userId);
            }
        }
    }
    
    /**
     * Get all document IDs that a user is currently in.
     */
    @NonNull
    public Set<Long> getDocumentsForUser(@NonNull Long userId) {
        Set<Long> userDocs = userDocuments.get(userId);
        if (userDocs == null) {
            return new HashSet<>();
        }
        return new HashSet<>(userDocs);
    }
    
    /**
     * Get all users currently in a document room.
     */
    @NonNull
    public Set<Long> getUsersInRoom(@NonNull Long documentId) {
        Set<Long> users = documentRooms.get(documentId);
        return users != null ? new HashSet<>(users) : new HashSet<>();
    }
    
    /**
     * Get user info for all users in a document room.
     */
    @NonNull
    public List<UserInfo> getUsersInfoInRoom(@NonNull Long documentId) {
        Map<Long, UserInfo> userMap = roomUsers.get(documentId);
        if (userMap == null || userMap.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(userMap.values());
    }
    
    /**
     * Check if a user is currently in a document room.
     */
    public boolean isUserInRoom(@NonNull Long documentId, @NonNull Long userId) {
        Set<Long> users = documentRooms.get(documentId);
        return users != null && users.contains(userId);
    }
    
    /**
     * Get the number of users in a document room.
     */
    public int getRoomUserCount(@NonNull Long documentId) {
        Set<Long> users = documentRooms.get(documentId);
        return users != null ? users.size() : 0;
    }
    
    /**
     * Inner class to store user information in a room.
     */
    public static class UserInfo {
        private final Long userId;
        private final String userName;
        private final Long joinedAt;
        
        public UserInfo(Long userId, String userName, Long joinedAt) {
            this.userId = userId;
            this.userName = userName;
            this.joinedAt = joinedAt;
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public String getUserName() {
            return userName;
        }
        
        public Long getJoinedAt() {
            return joinedAt;
        }
    }
}

