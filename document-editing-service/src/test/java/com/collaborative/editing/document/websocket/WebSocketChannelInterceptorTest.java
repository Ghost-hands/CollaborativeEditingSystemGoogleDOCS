package com.collaborative.editing.document.websocket;

import com.collaborative.editing.document.service.DocumentRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket Channel Interceptor Tests")
class WebSocketChannelInterceptorTest {

    @Mock
    private DocumentRoomService documentRoomService;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketChannelInterceptor interceptor;

    private Long documentId;
    private Long userId;
    private String userName;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId = 1L;
        userName = "testuser";
    }

    @Test
    @DisplayName("Allow subscription for authorized user")
    void testPreSend_Subscribe_Authorized() {
        String destination = "/topic/document/" + documentId + "/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        sessionAttributes.put("userName", userName);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        when(documentRoomService.canUserJoinRoom(documentId, userId)).thenReturn(true);
        when(documentRoomService.addUserToRoom(eq(documentId), eq(userId), anyString())).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(documentRoomService, times(1)).canUserJoinRoom(documentId, userId);
        verify(documentRoomService, times(1)).addUserToRoom(eq(documentId), eq(userId), anyString());
    }

    @Test
    @DisplayName("Deny subscription for unauthorized user")
    void testPreSend_Subscribe_Unauthorized() {
        String destination = "/topic/document/" + documentId + "/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        when(documentRoomService.canUserJoinRoom(documentId, userId)).thenReturn(false);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNull(result); // Subscription denied
        verify(documentRoomService, times(1)).canUserJoinRoom(documentId, userId);
        verify(documentRoomService, never()).addUserToRoom(any(), any(), any());
    }

    @Test
    @DisplayName("Handle subscription without user ID")
    void testPreSend_Subscribe_NoUserId() {
        String destination = "/topic/document/" + documentId + "/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(new HashMap<>());
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        Message<?> result = interceptor.preSend(message, messageChannel);

        // Should allow but not add to room
        assertNotNull(result);
        verify(documentRoomService, never()).canUserJoinRoom(any(), any());
    }

    @Test
    @DisplayName("Handle subscription to cursor topic")
    void testPreSend_Subscribe_CursorTopic() {
        String destination = "/topic/document/" + documentId + "/cursors";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        when(documentRoomService.canUserJoinRoom(documentId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        // Should not add to room for cursor topic
        verify(documentRoomService, never()).addUserToRoom(any(), any(), any());
    }

    @Test
    @DisplayName("Handle invalid document ID in destination")
    void testPreSend_Subscribe_InvalidDocumentId() {
        String destination = "/topic/document/invalid/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        Message<?> result = interceptor.preSend(message, messageChannel);

        // Should allow (invalid ID is logged but not blocked)
        assertNotNull(result);
        verify(documentRoomService, never()).canUserJoinRoom(any(), any());
    }

    @Test
    @DisplayName("Handle non-document subscription")
    void testPreSend_Subscribe_NonDocumentTopic() {
        String destination = "/topic/other/topic";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(documentRoomService, never()).canUserJoinRoom(any(), any());
    }

    @Test
    @DisplayName("Handle DISCONNECT command")
    void testPreSend_Disconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        // Disconnect handling is logged but cleanup happens elsewhere
    }

    @Test
    @DisplayName("Extract user ID from session attributes")
    void testExtractUserId_FromSessionAttributes() {
        String destination = "/topic/document/" + documentId + "/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        when(documentRoomService.canUserJoinRoom(documentId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(documentRoomService).canUserJoinRoom(documentId, userId);
    }

    @Test
    @DisplayName("Handle user ID as string in session")
    void testExtractUserId_StringType() {
        String destination = "/topic/document/" + documentId + "/operations";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId.toString());
        accessor.setSessionAttributes(sessionAttributes);
        
        Message<?> message = MessageBuilder.withPayload(new byte[0])
                .setHeaders(accessor)
                .build();

        when(documentRoomService.canUserJoinRoom(documentId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verify(documentRoomService).canUserJoinRoom(documentId, userId);
    }
}
