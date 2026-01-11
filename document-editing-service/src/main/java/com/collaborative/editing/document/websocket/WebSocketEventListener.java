package com.collaborative.editing.document.websocket;

import com.collaborative.editing.document.service.DocumentRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Map;

/**
 * WebSocket event listener to handle connection/disconnection events
 * and manage document room join/leave notifications.
 */
@Component
public class WebSocketEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    
    @Autowired
    private DocumentRoomService documentRoomService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        logger.debug("WebSocket connection established");
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        // Try to get userId from session attributes or headers
        Long userId = null;
        if (sessionAttributes != null) {
            Object userIdObj = sessionAttributes.get("userId");
            userId = parseLong(userIdObj);
        }
        
        // If not in session attributes, try to get from headers
        if (userId == null) {
            Map<String, List<String>> nativeHeaders = headerAccessor.toNativeHeaderMap();
            if (nativeHeaders != null) {
                List<String> userIdHeaderList = nativeHeaders.get("userId");
                if (userIdHeaderList != null && !userIdHeaderList.isEmpty()) {
                    userId = parseLong(userIdHeaderList.get(0));
                }
            }
        }
        
        if (userId != null) {
            try {
                // Get all documents this user is in
                java.util.Set<Long> userDocuments = documentRoomService.getDocumentsForUser(userId);
                
                // Remove user from all document rooms
                documentRoomService.removeUserFromAllRooms(userId);
                
                // Notify other users in each room
                for (Long documentId : userDocuments) {
                    Map<String, Object> leaveNotification = new java.util.HashMap<>();
                    leaveNotification.put("type", "user_left");
                    leaveNotification.put("documentId", documentId);
                    leaveNotification.put("userId", userId);
                    leaveNotification.put("userCount", documentRoomService.getRoomUserCount(documentId));
                    
                    messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", leaveNotification);
                    
                    logger.info("User left document room: documentId={}, userId={}, remainingUsers={}", 
                            documentId, userId, documentRoomService.getRoomUserCount(documentId));
                }
            } catch (Exception e) {
                logger.warn("Error handling disconnect for userId={}: {}", userId, e.getMessage());
            }
        } else {
            logger.debug("User disconnected but userId not found in session");
        }
    }
    
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        
        if (destination != null && destination.startsWith("/topic/document/")) {
            // Extract documentId from destination
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                try {
                    Long documentId = Long.parseLong(parts[3]);
                    
                    // Extract userId from session attributes
                    Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
                    if (sessionAttributes != null) {
                        Object userIdObj = sessionAttributes.get("userId");
                        Object userNameObj = sessionAttributes.get("userName");
                        
                        if (userIdObj != null) {
                            Long userId = parseLong(userIdObj);
                            String userName = userNameObj != null ? userNameObj.toString() : "User " + userId;
                            
                            if (userId != null && documentRoomService.canUserJoinRoom(documentId, userId)) {
                                // User successfully subscribed - notify others
                                Map<String, Object> joinNotification = new java.util.HashMap<>();
                                joinNotification.put("type", "user_joined");
                                joinNotification.put("documentId", documentId);
                                joinNotification.put("userId", userId);
                                joinNotification.put("userName", userName);
                                joinNotification.put("userCount", documentRoomService.getRoomUserCount(documentId));
                                
                                messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", joinNotification);
                                
                                logger.debug("User joined document room: documentId={}, userId={}, userName={}", 
                                        documentId, userId, userName);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid document ID in subscription: {}", destination);
                }
            }
        }
    }
    
    private Long parseLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

