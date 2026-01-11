package com.collaborative.editing.document.websocket;

import com.collaborative.editing.document.service.DocumentRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket Event Listener Tests")
@SuppressWarnings({"null", "unchecked"})
class WebSocketEventListenerTest {

    @Mock
    private DocumentRoomService documentRoomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    private Long documentId;
    private Long userId;
    private String userName;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId = 1L;
        userName = "TestUser";
    }

    @Test
    @DisplayName("Handle WebSocket connect - should log")
    void testHandleWebSocketConnectListener() {
        SessionConnectedEvent event = mock(SessionConnectedEvent.class);

        webSocketEventListener.handleWebSocketConnectListener(event);

        // Should not throw exception
        verifyNoInteractions(documentRoomService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Handle WebSocket disconnect - with userId in session")
    void testHandleWebSocketDisconnectListener_WithUserId() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(StompHeaderAccessor.wrap(any(org.springframework.messaging.Message.class))).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        
        Set<Long> userDocuments = Set.of(documentId);
        when(documentRoomService.getDocumentsForUser(anyLong())).thenReturn(userDocuments);
        when(documentRoomService.getRoomUserCount(anyLong())).thenReturn(0);
        doNothing().when(documentRoomService).removeUserFromAllRooms(anyLong());

        webSocketEventListener.handleWebSocketDisconnectListener(event);

        verify(documentRoomService, times(1)).removeUserFromAllRooms(anyLong());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Handle WebSocket disconnect - no userId")
    void testHandleWebSocketDisconnectListener_NoUserId() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        when(headerAccessor.getSessionAttributes()).thenReturn(new HashMap<>());
        when(StompHeaderAccessor.wrap(any())).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));

        webSocketEventListener.handleWebSocketDisconnectListener(event);

        verify(documentRoomService, never()).removeUserFromAllRooms(anyLong());
    }

    @Test
    @DisplayName("Handle WebSocket disconnect - userId from headers")
    void testHandleWebSocketDisconnectListener_UserIdFromHeaders() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        Map<String, Object> sessionAttributes = new HashMap<>();
        Map<String, java.util.List<String>> nativeHeaders = new HashMap<>();
        nativeHeaders.put("userId", java.util.Arrays.asList(userId.toString()));
        
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(headerAccessor.toNativeHeaderMap()).thenReturn(nativeHeaders);
        when(StompHeaderAccessor.wrap(any())).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        
        Set<Long> userDocuments = Set.of(documentId);
        when(documentRoomService.getDocumentsForUser(anyLong())).thenReturn(userDocuments);
        when(documentRoomService.getRoomUserCount(anyLong())).thenReturn(0);
        doNothing().when(documentRoomService).removeUserFromAllRooms(anyLong());

        webSocketEventListener.handleWebSocketDisconnectListener(event);

        verify(documentRoomService, times(1)).removeUserFromAllRooms(anyLong());
    }

    @Test
    @DisplayName("Handle WebSocket subscribe - valid document subscription")
    void testHandleWebSocketSubscribeListener_ValidDocument() {
        SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        String destination = "/topic/document/" + documentId + "/changes";
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        sessionAttributes.put("userName", userName);
        
        when(headerAccessor.getDestination()).thenReturn(destination);
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(StompHeaderAccessor.wrap(any(org.springframework.messaging.Message.class))).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        
        when(documentRoomService.canUserJoinRoom(anyLong(), anyLong())).thenReturn(true);
        when(documentRoomService.getRoomUserCount(anyLong())).thenReturn(1);

        webSocketEventListener.handleWebSocketSubscribeListener(event);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Handle WebSocket subscribe - invalid destination")
    void testHandleWebSocketSubscribeListener_InvalidDestination() {
        SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        when(headerAccessor.getDestination()).thenReturn("/topic/invalid");
        when(StompHeaderAccessor.wrap(any())).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));

        webSocketEventListener.handleWebSocketSubscribeListener(event);

        verify(documentRoomService, never()).canUserJoinRoom(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Handle WebSocket subscribe - user cannot join room")
    void testHandleWebSocketSubscribeListener_CannotJoin() {
        SessionSubscribeEvent event = mock(SessionSubscribeEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        String destination = "/topic/document/" + documentId + "/changes";
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        
        when(headerAccessor.getDestination()).thenReturn(destination);
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(StompHeaderAccessor.wrap(any(org.springframework.messaging.Message.class))).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        
        when(documentRoomService.canUserJoinRoom(anyLong(), anyLong())).thenReturn(false);

        webSocketEventListener.handleWebSocketSubscribeListener(event);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Handle WebSocket disconnect - exception handling")
    void testHandleWebSocketDisconnectListener_Exception() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        StompHeaderAccessor headerAccessor = mock(StompHeaderAccessor.class);
        
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(StompHeaderAccessor.wrap(any(org.springframework.messaging.Message.class))).thenReturn(headerAccessor);
        when(event.getMessage()).thenReturn(mock(org.springframework.messaging.Message.class));
        
        when(documentRoomService.getDocumentsForUser(anyLong()))
            .thenThrow(new RuntimeException("Service error"));

        // Should not throw exception
        webSocketEventListener.handleWebSocketDisconnectListener(event);
    }
}
