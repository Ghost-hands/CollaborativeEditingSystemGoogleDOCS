package com.collaborative.editing.document.websocket;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.CursorPositionDTO;
import com.collaborative.editing.common.dto.OperationDTO;
import com.collaborative.editing.document.service.CursorTrackingService;
import com.collaborative.editing.document.service.DocumentRoomService;
import com.collaborative.editing.document.service.DocumentService;
import com.collaborative.editing.document.service.OperationalTransformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@SuppressWarnings("null")
public class DocumentWebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentWebSocketController.class);
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private OperationalTransformationService otService;
    
    @Autowired
    private CursorTrackingService cursorTrackingService;
    
    @Autowired
    private DocumentRoomService documentRoomService;
    
    // Store document states and pending operations for OT
    private final Map<Long, DocumentState> documentStates = new ConcurrentHashMap<>();
    
    /**
     * Handle subscription to document room: send current users list.
     */
    @SubscribeMapping("/topic/document/{documentId}/users")
    public Map<String, Object> handleDocumentRoomSubscribe(@org.springframework.messaging.handler.annotation.DestinationVariable Long documentId) {
        List<DocumentRoomService.UserInfo> users = documentRoomService.getUsersInfoInRoom(documentId);
        Map<String, Object> response = new HashMap<>();
        response.put("type", "users_list");
        response.put("documentId", documentId);
        response.put("users", users);
        return response;
    }
    
    @MessageMapping("/document/edit")
    @NonNull
    public ChangeTrackingDTO handleDocumentEdit(@NonNull Map<String, Object> message) {
        Long documentId = Long.parseLong(message.get("documentId").toString());
        Long userId = Long.parseLong(message.get("userId").toString());
        
        // Verify user is in the document room and has permission
        if (!documentRoomService.isUserInRoom(documentId, userId)) {
            if (!documentRoomService.canUserJoinRoom(documentId, userId)) {
                logger.warn("Unauthorized edit attempt: userId={}, documentId={}", userId, documentId);
                throw new RuntimeException("User does not have permission to edit this document");
            }
            //if  User has permission but not in room then add them
            String userName = message.get("userName") != null ? 
                    message.get("userName").toString() : "User " + userId;
            documentRoomService.addUserToRoom(documentId, userId, userName);
        }
        
        // Check if this is an operation based edit or legacy content based edit
        if (message.containsKey("operation")) {
            return handleOperationEdit(documentId, userId, message);
        } else {
            return handleLegacyEdit(documentId, userId, message);
        }
    }
    
    @MessageMapping("/document/cursor")
    public void handleCursorUpdate(@NonNull Map<String, Object> message) {
        try {
            Long documentId = Long.parseLong(message.get("documentId").toString());
            Long userId = Long.parseLong(message.get("userId").toString());
            Integer position = message.get("position") != null ? 
                    Integer.parseInt(message.get("position").toString()) : null;
            String userName = message.get("userName") != null ? 
                    message.get("userName").toString() : "User " + userId;
            
            if (!documentRoomService.isUserInRoom(documentId, userId)) {
                if (!documentRoomService.canUserJoinRoom(documentId, userId)) {
                    logger.warn("Unauthorized cursor update: userId={}, documentId={}", userId, documentId);
                    return;
                }
                documentRoomService.addUserToRoom(documentId, userId, userName);
            }
            
            // Update cursor position (this stores the cursor with color)
            cursorTrackingService.updateCursor(documentId, userId, position, userName);
            
            // Get the cursor with color from the tracking service
            List<CursorPositionDTO> allCursors = cursorTrackingService.getCursorsForDocument(documentId);
            CursorPositionDTO cursor = allCursors.stream()
                    .filter(c -> c.getUserId().equals(userId))
                    .findFirst()
                    .orElse(new CursorPositionDTO(userId, documentId, position, userName, null));
            
            // Broadcast cursor update to all subscribers in the room (including sender)
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/cursors", cursor);
        } catch (Exception e) {
            // Log error but don't break the connection
            logger.error("Error handling cursor update: {}", e.getMessage(), e);
        }
    }
    
    private ChangeTrackingDTO handleOperationEdit(Long documentId, Long userId, Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> operationMap = (Map<String, Object>) message.get("operation");
        
        // Create operation from message
        OperationDTO operation = new OperationDTO();
        String operationTypeStr = operationMap.get("type").toString();
        operation.setType(OperationDTO.Type.valueOf(operationTypeStr));
        operation.setContent((String) operationMap.get("content"));
        
        // For INSERT operations, content is required
        if (operationMap.get("length") != null) {
            operation.setLength(Integer.parseInt(operationMap.get("length").toString()));
        } else {
            // For DELETE operations, length is mandatory
            if (operation.getType() == OperationDTO.Type.DELETE) {
                logger.error("❌ DELETE operation missing required 'length' field: documentId={}, userId={}, operationMap={}", 
                        documentId, userId, operationMap);
                throw new RuntimeException("DELETE operation must have a 'length' field");
            }
            operation.setLength(null);
        }
        
        if (operationMap.get("position") != null) {
            operation.setPosition(Integer.parseInt(operationMap.get("position").toString()));
        } else {
            logger.error("❌ Operation missing required 'position' field: documentId={}, userId={}, type={}, operationMap={}", 
                    documentId, userId, operationTypeStr, operationMap);
            throw new RuntimeException("Operation must have a 'position' field");
        }
        
        operation.setUserId(userId);
        operation.setDocumentId(documentId);
        
        // Server is the source of truth. operationId is assigned on server arrival time.
        // This ensures operations are ordered by when they arrive at the server (first come, first serve).
        Long serverAssignedOperationId = otService.generateOperationId();
        operation.setOperationId(serverAssignedOperationId);
        operation.setBaseVersion(operationMap.get("baseVersion") != null ? 
                Long.parseLong(operationMap.get("baseVersion").toString()) : 0L);
        
        logger.debug("📥 Received operation from client: userId={}, type={}, pos={}, assigned server operationId={}", 
                userId, operation.getType(), operation.getPosition(), serverAssignedOperationId);
        
        if (operation.getType() == OperationDTO.Type.DELETE) {
            if (operation.getLength() == null || operation.getLength() <= 0) {
                logger.error("❌ Invalid DELETE operation: length is null or <= 0: documentId={}, userId={}, length={}, position={}", 
                        documentId, userId, operation.getLength(), operation.getPosition());
                throw new RuntimeException("DELETE operation must have a positive length");
            }
            if (operation.getPosition() == null || operation.getPosition() < 0) {
                logger.error("❌ Invalid DELETE operation: position is null or < 0: documentId={}, userId={}, length={}, position={}", 
                        documentId, userId, operation.getLength(), operation.getPosition());
                throw new RuntimeException("DELETE operation must have a valid position");
            }
            logger.debug("✅ Validated DELETE operation: documentId={}, userId={}, length={}, position={}", 
                    documentId, userId, operation.getLength(), operation.getPosition());
        }
        
        // Get current document state
        DocumentState state = documentStates.computeIfAbsent(documentId, 
                k -> {
                    try {
                        String currentContent = documentService.getDocumentById(documentId).getContent();
                        // Ensure content is never null - empty string for empty documents
                        String safeContent = (currentContent != null) ? currentContent : "";
                        // Initialize with version 0 to allow real-time editing from the start
                        DocumentState newState = new DocumentState(safeContent, 0L);
                        logger.info("✅ Initialized DocumentState for documentId={} with version=0, contentLength={}, isEmpty={}", 
                                documentId, newState.getContent().length(), safeContent.isEmpty());
                        return newState;
                    } catch (Exception e) {
                        logger.warn("⚠️ Error initializing DocumentState for documentId={}, using empty state", documentId, e);
                        // Return empty state with version 0 to allow operations
                        return new DocumentState("", 0L);
                    }
                });
        

        try {
            String dbContent = documentService.getDocumentById(documentId).getContent();
            String safeDbContent = (dbContent != null) ? dbContent : "";
            String currentStateContent = state.getContent() != null ? state.getContent() : "";
            
            // Only sync if content differs AND there are no pending operations (not actively editing)
            // OR if the difference is significant (more than just a few characters - likely external update)
            boolean hasPendingOps = !state.getPendingOperations().isEmpty();
            int contentDiff = Math.abs(safeDbContent.length() - currentStateContent.length());
            
            if (!safeDbContent.equals(currentStateContent)) {
                // If there are pending operations, only sync if difference is significant (external update)
                // Otherwise, trust the WebSocket state during active editing
                if (hasPendingOps && contentDiff <= 5) {
                    logger.debug("⚠️ Skipping state sync during active editing: documentId={}, stateLength={}, dbLength={}, pendingOps={}", 
                            documentId, currentStateContent.length(), safeDbContent.length(), state.getPendingOperations().size());
                } else {
                    logger.info("🔄 Syncing WebSocket state with database for documentId={}: stateLength={}, dbLength={}, pendingOps={}", 
                            documentId, currentStateContent.length(), safeDbContent.length(), state.getPendingOperations().size());
                    state.setContent(safeDbContent);
                    state.setVersion(0L);
                    if (contentDiff > 5 || !hasPendingOps) {
                        state.getPendingOperations().clear();
                        logger.info("✅ WebSocket state synced with database for documentId={} (cleared pending ops)", documentId);
                    } else {
                        logger.info("✅ WebSocket state synced with database for documentId={} (kept pending ops)", documentId);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ Error syncing state with database for documentId={}, continuing with current state", documentId, e);
        }
        

        if (state.getContent() == null) {
            logger.warn("⚠️ DocumentState content was null for documentId={}, resetting to empty string", documentId);
            state.setContent("");
        }
        

        List<OperationDTO> allPendingOps = state.getPendingOperations();
        List<OperationDTO> concurrentOps = new ArrayList<>();
        Long operationBaseVersion = operation.getBaseVersion() != null ? operation.getBaseVersion() : 0L;
        Long operationId = operation.getOperationId();
        

        for (OperationDTO pendingOp : allPendingOps) {
            Long pendingBaseVersion = pendingOp.getBaseVersion() != null ? pendingOp.getBaseVersion() : 0L;
            Long pendingOpId = pendingOp.getOperationId();
            
            // Include if pending operation's baseVersion > this operation's baseVersion
            // OR if baseVersion is same but operationId is greater (created later)
            boolean isConcurrent = false;
            if (pendingBaseVersion > operationBaseVersion) {
                isConcurrent = true;
            } else if (pendingBaseVersion.equals(operationBaseVersion)) {
                // Same baseVersion - use operationId to determine order
                if (operationId != null && pendingOpId != null && pendingOpId > operationId) {
                    isConcurrent = true;
                }
            }
            
            if (isConcurrent) {
                concurrentOps.add(pendingOp);
            }
        }
        
        // Sort concurrent operations by operationId to ensure correct transformation order
        // Operations must be transformed in the order they were applied
        concurrentOps.sort((op1, op2) -> {
            Long id1 = op1.getOperationId();
            Long id2 = op2.getOperationId();
            if (id1 == null && id2 == null) return 0;
            if (id1 == null) return -1;
            if (id2 == null) return 1;
            return id1.compareTo(id2);
        });
        
        if (!concurrentOps.isEmpty() && operation.getType() == OperationDTO.Type.INSERT) {
            logger.debug("🔄 Transforming INSERT operation: opId={}, pos={}, content={}, concurrentOps={} (sorted by opId)", 
                    operationId, operation.getPosition(), operation.getContent(), concurrentOps.size());
            // Log first few concurrent operations for debugging
            for (int i = 0; i < Math.min(3, concurrentOps.size()); i++) {
                OperationDTO concurrent = concurrentOps.get(i);
                logger.debug("  Concurrent op[{}]: opId={}, type={}, pos={}, content={}", 
                        i, concurrent.getOperationId(), concurrent.getType(), 
                        concurrent.getPosition(), concurrent.getContent() != null ? concurrent.getContent() : "DELETE len=" + concurrent.getLength());
            }
        }
        
        // Transform the operation against concurrent operations
        // This is the core OT transformation that preserves user intention
        // The server transforms the operation so it can be applied correctly
        // after all concurrent operations have been applied
        OperationDTO transformedOp = otService.transformAgainstOperations(operation, concurrentOps);
        

        if (operation.getType() == OperationDTO.Type.DELETE) {
            logger.debug("🔄 DELETE operation transformation: originalLength={}, transformedLength={}, originalPos={}, transformedPos={}, concurrentOps={}", 
                    operation.getLength(), transformedOp.getLength(), operation.getPosition(), 
                    transformedOp.getPosition(), concurrentOps.size());
        }
        
        //  Never broadcast RETAIN operations. they're only for internal transformation
        // If transformation resulted in RETAIN, it means the operation is a no-op and shouldn't be broadcast
        if (transformedOp.getType() == OperationDTO.Type.RETAIN) {
            logger.warn("⚠️ Transformation resulted in RETAIN operation (no-op) - not broadcasting: documentId={}, userId={}, operationId={}, originalType={}", 
                    documentId, userId, operation.getOperationId(), operation.getType());
            // Return a dummy change tracking object but don't broadcast
            ChangeTrackingDTO change = new ChangeTrackingDTO();
            change.setDocumentId(documentId);
            change.setUserId(userId);
            change.setChangeType("RETAIN");
            change.setContent(state.getContent());
            return change;
        }
        
        // Apply the transformed operation to the document state
        // Ensure empty documents are handled correctly
        String currentStateContent = state.getContent() != null ? state.getContent() : "";
        String newContent = otService.applyOperation(currentStateContent, transformedOp);
        
        // For DELETE operations, even if applyOperation returns unchanged content,
        // we should still broadcast the operation if it has a valid length > 0
        // This ensures DELETE operations are always broadcast to other users
        boolean shouldBroadcast = true;
        if (transformedOp.getType() == OperationDTO.Type.DELETE) {
            // Check if DELETE operation is valid
            if (transformedOp.getLength() == null || transformedOp.getLength() <= 0) {
                // Zero-length DELETE - don't broadcast (it's a no-op from transformation)
                logger.debug("Skipping broadcast of zero-length DELETE operation: documentId={}, userId={}, operationId={}", 
                        documentId, userId, transformedOp.getOperationId());
                shouldBroadcast = false;
            } else if (newContent.equals(currentStateContent)) {
                // Content didn't change but DELETE has valid length - this might be because
                // the content to delete doesn't exist at that position, but we should still broadcast
                // so other clients can apply it to their state
                logger.warn("⚠️ DELETE operation resulted in no content change but has valid length - will broadcast anyway: documentId={}, userId={}, position={}, length={}, docLength={}", 
                        documentId, userId, transformedOp.getPosition(), transformedOp.getLength(), currentStateContent.length());
                // Still broadcast - let clients handle it
            }
        }
        
        // Only update state and broadcast if operation is valid
        if (shouldBroadcast) {
            state.setContent(newContent);
            state.setVersion(state.getVersion() + 1);
            
            logger.debug("Applied operation: documentId={}, version={}, type={}, position={}, contentLength={} -> newLength={}", 
                    documentId, state.getVersion(), transformedOp.getType(), transformedOp.getPosition(), 
                    currentStateContent.length(), newContent.length());
        } else {
            // Operation is invalid (zero-length DELETE) - return early without broadcasting
            ChangeTrackingDTO change = new ChangeTrackingDTO();
            change.setDocumentId(documentId);
            change.setUserId(userId);
            change.setChangeType("SKIP");
            change.setContent(currentStateContent);
            return change;
        }
        
        // Ensure operationId is set (should already be set, but double-check for safety)
        if (transformedOp.getOperationId() == null) {
            transformedOp.setOperationId(otService.generateOperationId());
            logger.warn("Generated operationId for operation that was missing one: documentId={}, userId={}", 
                    documentId, userId);
        }
        
        // Add to pending operations
        state.getPendingOperations().add(transformedOp);
        
        // Clean up old operations (keep last 100)
        if (state.getPendingOperations().size() > 100) {
            state.getPendingOperations().remove(0);
        }
        
        // Update document in database immediately for every operation
        // This ensures content is persisted even at version 0, preventing data loss
        try {
            documentService.editDocument(documentId, userId, newContent);
            logger.debug("Document content updated in database: documentId={}, version={}, contentLength={}", 
                    documentId, state.getVersion(), newContent.length());
        } catch (Exception e) {
            logger.error("Failed to update document in database: documentId={}, userId={}, error={}", 
                    documentId, userId, e.getMessage(), e);
            // Don't throw - continue with broadcasting even if DB update fails
            // The operation should still be broadcasted to other users
        }
        
        // Track the change with the correct content
        // For INSERT: use the inserted content (transformedOp.getContent())
        // For DELETE: use the deleted content (we need to extract it from old content)
        String changeContent = null;
        if (transformedOp.getType() == OperationDTO.Type.INSERT) {
            // For INSERT operations, use the content that was inserted
            changeContent = transformedOp.getContent();
        } else if (transformedOp.getType() == OperationDTO.Type.DELETE) {
            // For DELETE operations, extract what was deleted from the old content
            int deletePos = transformedOp.getPosition();
            int deleteLength = transformedOp.getLength() != null ? transformedOp.getLength() : 0;
            if (deletePos >= 0 && deletePos < currentStateContent.length() && deleteLength > 0) {
                int endPos = Math.min(deletePos + deleteLength, currentStateContent.length());
                changeContent = currentStateContent.substring(deletePos, endPos);
            } else {
                // Fallback: use empty string or indicate deletion
                changeContent = "";
            }
        }
        
        // Track the change with the correct content
        ChangeTrackingDTO change = documentService.trackChange(documentId, userId, transformedOp.getType().toString(), 
                changeContent, transformedOp.getPosition());
        
        // Convert OperationDTO to Map for broadcasting
        Map<String, Object> operationBroadcastMap = new HashMap<>();
        operationBroadcastMap.put("type", transformedOp.getType().toString());
        operationBroadcastMap.put("content", transformedOp.getContent());
        operationBroadcastMap.put("length", transformedOp.getLength());
        operationBroadcastMap.put("position", transformedOp.getPosition());
        operationBroadcastMap.put("userId", transformedOp.getUserId());
        operationBroadcastMap.put("documentId", transformedOp.getDocumentId());
        operationBroadcastMap.put("operationId", transformedOp.getOperationId());
        operationBroadcastMap.put("baseVersion", transformedOp.getBaseVersion());
        
        // Broadcast transformed operation to all subscribers in the document room (including sender for confirmation)
        Map<String, Object> broadcastMessage = new HashMap<>();
        broadcastMessage.put("operation", operationBroadcastMap);
        broadcastMessage.put("documentId", documentId);
        broadcastMessage.put("userId", userId);
        // Don't send WebSocket state version - it's just an operation counter
        // All clients should work from baseVersion 0 for proper real-time sync
        // The actual document version is only relevant when creating versions
        broadcastMessage.put("timestamp", System.currentTimeMillis()); // Add timestamp for ordering
        
        // Broadcast to operations channel - this goes to ALL users in the document room
        String destination = "/topic/document/" + documentId + "/operations";
        messagingTemplate.convertAndSend(destination, broadcastMessage);
        
        // Enhanced logging for DELETE operations
        if (transformedOp.getType() == OperationDTO.Type.DELETE) {
            logger.info("📤🗑️ Broadcasted DELETE operation to {}: documentId={}, userId={}, operationId={}, position={}, length={}, beforeLength={}, afterLength={}", 
                    destination, documentId, userId, transformedOp.getOperationId(), 
                    transformedOp.getPosition(), transformedOp.getLength(),
                    currentStateContent.length(), newContent.length());
        } else {
            logger.info("📤 Broadcasted operation to {}: documentId={}, userId={}, operationId={}, type={}, position={}, content={}", 
                    destination, documentId, userId, transformedOp.getOperationId(), 
                    transformedOp.getType(), transformedOp.getPosition(),
                    transformedOp.getContent() != null ? transformedOp.getContent().substring(0, Math.min(20, transformedOp.getContent().length())) : "DELETE " + transformedOp.getLength());
        }
        
        return change;
    }
    
    private ChangeTrackingDTO handleLegacyEdit(Long documentId, Long userId, Map<String, Object> message) {
        String changeType = message.get("changeType").toString();
        String content = message.get("content").toString();
        Integer position = message.get("position") != null ? 
                Integer.parseInt(message.get("position").toString()) : null;
        
        // Get current document state
        DocumentState state = documentStates.computeIfAbsent(documentId, 
                k -> new DocumentState(documentService.getDocumentById(documentId).getContent(), 0L));
        
        // Convert legacy edit to operation
        String oldContent = state.getContent();
        OperationDTO operation = otService.createOperationFromChange(
                oldContent, content, position, userId, documentId, state.getVersion());
        
        // Get concurrent operations
        List<OperationDTO> concurrentOps = state.getPendingOperations();
        
        // Transform the operation
        OperationDTO transformedOp = otService.transformAgainstOperations(operation, concurrentOps);
        
        // Apply the transformed operation
        String newContent = otService.applyOperation(oldContent, transformedOp);
        state.setContent(newContent);
        state.setVersion(state.getVersion() + 1);
        
        // Ensure operationId is set (should already be set from createOperationFromChange, but double-check)
        if (transformedOp.getOperationId() == null) {
            transformedOp.setOperationId(otService.generateOperationId());
            logger.warn("Generated operationId for legacy operation that was missing one: documentId={}, userId={}", 
                    documentId, userId);
        }
        
        state.getPendingOperations().add(transformedOp);
        
        // Clean up old operations
        if (state.getPendingOperations().size() > 100) {
            state.getPendingOperations().remove(0);
        }
        
        // Update document
        documentService.editDocument(documentId, userId, newContent);
        
        ChangeTrackingDTO change = documentService.trackChange(
                documentId, userId, changeType, newContent, position);
        
        // Broadcast to all subscribers
        messagingTemplate.convertAndSend("/topic/document/" + documentId, change);
        
        return change;
    }
    
    /**
     * Reset document state (used after revert or content update)
     * This ensures real-time editing works correctly after document changes
     * The state will be reinitialized with current database content on the next operation
     */
    public void resetDocumentState(Long documentId) {
        logger.info("🔄 Resetting WebSocket state for documentId={}", documentId);
        // Remove existing state - it will be recreated on next operation with current DB content
        DocumentState removed = documentStates.remove(documentId);
        if (removed != null) {
            logger.info("✅ WebSocket state removed for documentId={} (had {} pending operations)", 
                    documentId, removed.getPendingOperations().size());
        } else {
            logger.info("ℹ️ No WebSocket state found for documentId={} (may not have been initialized yet)", documentId);
        }
        logger.info("✅ WebSocket state reset for documentId={} - will reinitialize with DB content on next operation", documentId);
    }
    
    // Inner class to track document state
    private static class DocumentState {
        private String content;
        private Long version;
        private List<OperationDTO> pendingOperations = new ArrayList<>();
        
        public DocumentState(String content, Long version) {
            this.content = content;
            this.version = version;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public Long getVersion() {
            return version;
        }
        
        public void setVersion(Long version) {
            this.version = version;
        }
        
        public List<OperationDTO> getPendingOperations() {
            return pendingOperations;
        }
    }
}

