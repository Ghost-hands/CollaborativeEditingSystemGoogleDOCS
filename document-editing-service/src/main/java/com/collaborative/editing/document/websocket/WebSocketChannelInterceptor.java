package com.collaborative.editing.document.websocket;

import com.collaborative.editing.document.service.DocumentRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * WebSocket channel interceptor to validate subscriptions and manage document rooms.
 * Ensures only authorized users can subscribe to document-specific topics.
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);
    
    @Autowired
    private DocumentRoomService documentRoomService;
    
    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            
            // Check if this is a document-specific subscription
            if (destination != null && destination.startsWith("/topic/document/")) {
                // Extract documentId from destination
                // Format: /topic/document/{documentId} or /topic/document/{documentId}/operations or /topic/document/{documentId}/cursors
                String[] parts = destination.split("/");
                if (parts.length >= 4) {
                    try {
                        Long documentId = Long.parseLong(parts[3]);
                        
                        // Extract userId from session attributes or headers
                        Long userId = extractUserId(accessor);
                        String userName = extractUserName(accessor);
                        
                        if (userId == null) {
                            logger.warn("User ID not found in WebSocket subscription: destination={}", destination);
                            // Allow subscription but will be validated in message handlers
                        } else {
                            // Check if user has permission to join the document room
                            if (documentRoomService.canUserJoinRoom(documentId, userId)) {
                                // Add user to room (only on first subscription, not for each topic)
                                if (destination.endsWith("/operations") || destination.endsWith("/cursors") || 
                                    !destination.contains("/operations") && !destination.contains("/cursors")) {
                                    // Only add once per document (for main topic or operations topic)
                                    if (!destination.contains("/cursors")) {
                                        documentRoomService.addUserToRoom(documentId, userId, 
                                                userName != null ? userName : "User " + userId);
                                    }
                                }
                                logger.debug("User authorized to subscribe: userId={}, documentId={}, destination={}", 
                                        userId, documentId, destination);
                            } else {
                                logger.warn("User denied subscription: userId={}, documentId={}, destination={}", 
                                        userId, documentId, destination);
                                // Deny subscription by returning null
                                return null;
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid document ID in subscription destination: {}", destination);
                    }
                }
            }
        } else if (accessor != null && accessor.getCommand() == StompCommand.DISCONNECT) {
            // Handle disconnect - remove user from rooms
            Long userId = extractUserId(accessor);
            if (userId != null) {
                // Note: We don't have documentId here, so we'll handle cleanup in the disconnect handler
                logger.debug("User disconnecting: userId={}", userId);
            }
        }
        
        return message;
    }
    
    /**
     * Extract user ID from WebSocket session attributes or headers.
     * In a real implementation, this would come from authentication token or session.
     */
    private Long extractUserId(StompHeaderAccessor accessor) {
        // Try to get from session attributes
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.containsKey("userId")) {
            Object userIdObj = sessionAttributes.get("userId");
            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    return Long.parseLong((String) userIdObj);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        // Try to get from native headers (STOMP headers)
        Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();
        if (nativeHeaders != null) {
            List<String> userIdHeaderList = nativeHeaders.get("userId");
            if (userIdHeaderList != null && !userIdHeaderList.isEmpty()) {
                String userIdHeader = userIdHeaderList.get(0);
                try {
                    return Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract user name from WebSocket session attributes or headers.
     */
    private String extractUserName(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.containsKey("userName")) {
            Object userNameObj = sessionAttributes.get("userName");
            if (userNameObj instanceof String) {
                return (String) userNameObj;
            }
        }
        
        Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();
        if (nativeHeaders != null) {
            List<String> userNameHeaderList = nativeHeaders.get("userName");
            if (userNameHeaderList != null && !userNameHeaderList.isEmpty()) {
                return userNameHeaderList.get(0);
            }
        }
        
        return null;
    }
}

