package com.collaborative.editing.document.websocket;

import com.collaborative.editing.common.dto.OperationDTO;
import com.collaborative.editing.document.service.CursorTrackingService;
import com.collaborative.editing.document.service.DocumentRoomService;
import com.collaborative.editing.document.service.DocumentService;
import com.collaborative.editing.document.service.OperationalTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document WebSocket Controller Tests")
class DocumentWebSocketControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private OperationalTransformationService otService;

    @Mock
    private CursorTrackingService cursorTrackingService;

    @Mock
    private DocumentRoomService documentRoomService;

    @InjectMocks
    private DocumentWebSocketController webSocketController;

    private Long documentId;
    private Long userId1;
    private String userName1;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId1 = 1L;
        userName1 = "User1";
    }

    @Test
    @DisplayName("Handle operation edit - INSERT operation")
    void testHandleOperationEdit_Insert() {
        Map<String, Object> message = createOperationMessage("INSERT", "Hello", null, 0, userId1, 0L);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(true);
        when(documentService.getDocumentById(documentId))
            .thenReturn(createDocumentDTO("", documentId, userId1));
        
        OperationDTO operation = new OperationDTO();
        operation.setType(OperationDTO.Type.INSERT);
        operation.setContent("Hello");
        operation.setPosition(0);
        operation.setUserId(userId1);
        operation.setDocumentId(documentId);
        operation.setOperationId(1L);
        operation.setBaseVersion(0L);
        
        OperationDTO transformedOp = new OperationDTO();
        transformedOp.setType(OperationDTO.Type.INSERT);
        transformedOp.setContent("Hello");
        transformedOp.setPosition(0);
        transformedOp.setUserId(userId1);
        transformedOp.setDocumentId(documentId);
        transformedOp.setOperationId(1L);
        transformedOp.setBaseVersion(0L);
        
        when(otService.transformAgainstOperations(any(OperationDTO.class), anyList()))
            .thenReturn(transformedOp);
        when(otService.applyOperation(anyString(), any(OperationDTO.class)))
            .thenReturn("Hello");
        when(otService.generateOperationId()).thenReturn(1L);
        when(documentService.editDocument(anyLong(), anyLong(), anyString()))
            .thenReturn(createDocumentDTO("Hello", documentId, userId1));
        when(documentService.trackChange(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createChangeTrackingDTO());

        var result = webSocketController.handleDocumentEdit(message);

        assertNotNull(result);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            ArgumentMatchers.<String>any(), ArgumentMatchers.<Object>any());
        verify(documentService, times(1)).editDocument(eq(documentId), eq(userId1), eq("Hello"));
    }

    @Test
    @DisplayName("Handle operation edit - DELETE operation")
    void testHandleOperationEdit_Delete() {
        Map<String, Object> message = createOperationMessage("DELETE", null, 5, 0, userId1, 0L);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(true);
        when(documentService.getDocumentById(documentId))
            .thenReturn(createDocumentDTO("Hello", documentId, userId1));
        
        OperationDTO operation = new OperationDTO();
        operation.setType(OperationDTO.Type.DELETE);
        operation.setLength(5);
        operation.setPosition(0);
        operation.setUserId(userId1);
        operation.setDocumentId(documentId);
        operation.setOperationId(1L);
        operation.setBaseVersion(0L);
        
        OperationDTO transformedOp = new OperationDTO();
        transformedOp.setType(OperationDTO.Type.DELETE);
        transformedOp.setLength(5);
        transformedOp.setPosition(0);
        transformedOp.setUserId(userId1);
        transformedOp.setDocumentId(documentId);
        transformedOp.setOperationId(1L);
        transformedOp.setBaseVersion(0L);
        
        when(otService.transformAgainstOperations(any(OperationDTO.class), anyList()))
            .thenReturn(transformedOp);
        when(otService.applyOperation(anyString(), any(OperationDTO.class)))
            .thenReturn("");
        when(otService.generateOperationId()).thenReturn(1L);
        when(documentService.editDocument(anyLong(), anyLong(), anyString()))
            .thenReturn(createDocumentDTO("", documentId, userId1));
        when(documentService.trackChange(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createChangeTrackingDTO());

        var result = webSocketController.handleDocumentEdit(message);

        assertNotNull(result);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            ArgumentMatchers.<String>any(), ArgumentMatchers.<Object>any());
    }

    @Test
    @DisplayName("Handle operation edit - user not in room, add to room")
    void testHandleOperationEdit_UserNotInRoom() {
        Map<String, Object> message = createOperationMessage("INSERT", "Hello", null, 0, userId1, 0L);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(false);
        when(documentRoomService.canUserJoinRoom(documentId, userId1)).thenReturn(true);
        when(documentService.getDocumentById(documentId))
            .thenReturn(createDocumentDTO("", documentId, userId1));
        
        OperationDTO transformedOp = new OperationDTO();
        transformedOp.setType(OperationDTO.Type.INSERT);
        transformedOp.setContent("Hello");
        transformedOp.setPosition(0);
        transformedOp.setOperationId(1L);
        
        when(otService.transformAgainstOperations(any(OperationDTO.class), anyList()))
            .thenReturn(transformedOp);
        when(otService.applyOperation(anyString(), any(OperationDTO.class)))
            .thenReturn("Hello");
        when(otService.generateOperationId()).thenReturn(1L);
        when(documentService.editDocument(anyLong(), anyLong(), anyString()))
            .thenReturn(createDocumentDTO("Hello", documentId, userId1));
        when(documentService.trackChange(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createChangeTrackingDTO());

        var result = webSocketController.handleDocumentEdit(message);

        assertNotNull(result);
        verify(documentRoomService, times(1)).addUserToRoom(eq(documentId), eq(userId1), anyString());
    }

    @Test
    @DisplayName("Handle operation edit - unauthorized user")
    void testHandleOperationEdit_Unauthorized() {
        Map<String, Object> message = createOperationMessage("INSERT", "Hello", null, 0, userId1, 0L);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(false);
        when(documentRoomService.canUserJoinRoom(documentId, userId1)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            webSocketController.handleDocumentEdit(message);
        });
    }

    @Test
    @DisplayName("Handle operation edit - version 0 empty document")
    void testHandleOperationEdit_Version0EmptyDocument() {
        Map<String, Object> message = createOperationMessage("INSERT", "H", null, 0, userId1, 0L);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(true);
        when(documentService.getDocumentById(documentId))
            .thenReturn(createDocumentDTO("", documentId, userId1));
        
        OperationDTO transformedOp = new OperationDTO();
        transformedOp.setType(OperationDTO.Type.INSERT);
        transformedOp.setContent("H");
        transformedOp.setPosition(0);
        transformedOp.setOperationId(1L);
        transformedOp.setBaseVersion(0L);
        
        when(otService.transformAgainstOperations(any(OperationDTO.class), anyList()))
            .thenReturn(transformedOp);
        when(otService.applyOperation(eq(""), any(OperationDTO.class)))
            .thenReturn("H");
        when(otService.generateOperationId()).thenReturn(1L);
        when(documentService.editDocument(anyLong(), anyLong(), anyString()))
            .thenReturn(createDocumentDTO("H", documentId, userId1));
        when(documentService.trackChange(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createChangeTrackingDTO());

        var result = webSocketController.handleDocumentEdit(message);

        assertNotNull(result);
        verify(otService, times(1)).applyOperation(eq(""), any(OperationDTO.class));
        verify(documentService, times(1)).editDocument(eq(documentId), eq(userId1), eq("H"));
    }

    @Test
    @DisplayName("Handle cursor update")
    void testHandleCursorUpdate() {
        Map<String, Object> message = new HashMap<>();
        message.put("documentId", documentId.toString());
        message.put("userId", userId1.toString());
        message.put("position", "5");
        message.put("userName", userName1);
        
        when(documentRoomService.isUserInRoom(documentId, userId1)).thenReturn(true);

        webSocketController.handleCursorUpdate(message);

        verify(cursorTrackingService, times(1)).updateCursor(eq(documentId), eq(userId1), eq(5), eq(userName1));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
            ArgumentMatchers.<String>any(), ArgumentMatchers.<Object>any());
    }


    @Test
    @DisplayName("Handle document room subscribe - returns users list")
    void testHandleDocumentRoomSubscribe() {
        DocumentRoomService.UserInfo userInfo = new DocumentRoomService.UserInfo(userId1, userName1, System.currentTimeMillis());
        
        when(documentRoomService.getUsersInfoInRoom(documentId))
            .thenReturn(java.util.Collections.singletonList(userInfo));

        Map<String, Object> result = webSocketController.handleDocumentRoomSubscribe(documentId);

        assertNotNull(result);
        assertEquals("users_list", result.get("type"));
        assertEquals(documentId, result.get("documentId"));
        assertNotNull(result.get("users"));
    }

    // Helper methods
    private Map<String, Object> createOperationMessage(String type, String content, Integer length, 
                                                       Integer position, Long userId, Long baseVersion) {
        Map<String, Object> message = new HashMap<>();
        message.put("documentId", documentId.toString());
        message.put("userId", userId.toString());
        message.put("userName", "User" + userId);
        
        Map<String, Object> operation = new HashMap<>();
        operation.put("type", type);
        if (content != null) {
            operation.put("content", content);
        }
        if (length != null) {
            operation.put("length", length.toString());
        }
        operation.put("position", position.toString());
        operation.put("userId", userId.toString());
        operation.put("documentId", documentId.toString());
        operation.put("operationId", "1");
        operation.put("baseVersion", baseVersion.toString());
        
        message.put("operation", operation);
        return message;
    }

    private com.collaborative.editing.common.dto.DocumentDTO createDocumentDTO(String content, Long docId, Long ownerId) {
        com.collaborative.editing.common.dto.DocumentDTO dto = new com.collaborative.editing.common.dto.DocumentDTO();
        dto.setId(docId);
        dto.setContent(content);
        dto.setOwnerId(ownerId);
        return dto;
    }

    private com.collaborative.editing.common.dto.ChangeTrackingDTO createChangeTrackingDTO() {
        com.collaborative.editing.common.dto.ChangeTrackingDTO dto = 
            new com.collaborative.editing.common.dto.ChangeTrackingDTO();
        dto.setDocumentId(documentId);
        dto.setUserId(userId1);
        dto.setChangeType("INSERT");
        return dto;
    }
}

