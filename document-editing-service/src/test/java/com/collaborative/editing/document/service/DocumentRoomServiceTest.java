package com.collaborative.editing.document.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document Room Service Tests")
class DocumentRoomServiceTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentRoomService documentRoomService;

    private Long documentId;
    private Long ownerId;
    private Long collaboratorId;
    private Long unauthorizedUserId;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        ownerId = 1L;
        collaboratorId = 2L;
        unauthorizedUserId = 3L;
    }

    @Test
    @DisplayName("Check if owner can join room")
    void testCanUserJoinRoom_Owner() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);

        boolean result = documentRoomService.canUserJoinRoom(documentId, ownerId);

        assertTrue(result);
        verify(documentService, times(1)).canUserEditDocument(documentId, ownerId);
    }

    @Test
    @DisplayName("Check if collaborator can join room")
    void testCanUserJoinRoom_Collaborator() {
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        boolean result = documentRoomService.canUserJoinRoom(documentId, collaboratorId);

        assertTrue(result);
        verify(documentService, times(1)).canUserEditDocument(documentId, collaboratorId);
    }

    @Test
    @DisplayName("Check if unauthorized user cannot join room")
    void testCanUserJoinRoom_Unauthorized() {
        when(documentService.canUserEditDocument(documentId, unauthorizedUserId)).thenReturn(false);

        boolean result = documentRoomService.canUserJoinRoom(documentId, unauthorizedUserId);

        assertFalse(result);
        verify(documentService, times(1)).canUserEditDocument(documentId, unauthorizedUserId);
    }

    @Test
    @DisplayName("Add owner to room successfully")
    void testAddUserToRoom_Owner() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);

        boolean result = documentRoomService.addUserToRoom(documentId, ownerId, "Owner");

        assertTrue(result);
        assertTrue(documentRoomService.isUserInRoom(documentId, ownerId));
        assertEquals(1, documentRoomService.getRoomUserCount(documentId));
    }

    @Test
    @DisplayName("Add collaborator to room successfully")
    void testAddUserToRoom_Collaborator() {
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        boolean result = documentRoomService.addUserToRoom(documentId, collaboratorId, "Collaborator");

        assertTrue(result);
        assertTrue(documentRoomService.isUserInRoom(documentId, collaboratorId));
    }

    @Test
    @DisplayName("Add unauthorized user to room - should fail")
    void testAddUserToRoom_Unauthorized() {
        when(documentService.canUserEditDocument(documentId, unauthorizedUserId)).thenReturn(false);

        boolean result = documentRoomService.addUserToRoom(documentId, unauthorizedUserId, "Unauthorized");

        assertFalse(result);
        assertFalse(documentRoomService.isUserInRoom(documentId, unauthorizedUserId));
    }

    @Test
    @DisplayName("Add multiple users to same room")
    void testAddUserToRoom_MultipleUsers() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");
        documentRoomService.addUserToRoom(documentId, collaboratorId, "Collaborator");

        assertEquals(2, documentRoomService.getRoomUserCount(documentId));
        assertTrue(documentRoomService.isUserInRoom(documentId, ownerId));
        assertTrue(documentRoomService.isUserInRoom(documentId, collaboratorId));
    }

    @Test
    @DisplayName("Remove user from room")
    void testRemoveUserFromRoom() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");

        documentRoomService.removeUserFromRoom(documentId, ownerId);

        assertFalse(documentRoomService.isUserInRoom(documentId, ownerId));
        assertEquals(0, documentRoomService.getRoomUserCount(documentId));
    }

    @Test
    @DisplayName("Remove user from room - room should be cleaned up when empty")
    void testRemoveUserFromRoom_CleanupEmptyRoom() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");

        documentRoomService.removeUserFromRoom(documentId, ownerId);

        assertEquals(0, documentRoomService.getRoomUserCount(documentId));
        assertTrue(documentRoomService.getUsersInRoom(documentId).isEmpty());
    }

    @Test
    @DisplayName("Remove user from all rooms")
    void testRemoveUserFromAllRooms() {
        Long doc1 = 1L;
        Long doc2 = 2L;
        
        when(documentService.canUserEditDocument(doc1, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(doc2, ownerId)).thenReturn(true);

        documentRoomService.addUserToRoom(doc1, ownerId, "Owner");
        documentRoomService.addUserToRoom(doc2, ownerId, "Owner");

        documentRoomService.removeUserFromAllRooms(ownerId);

        assertFalse(documentRoomService.isUserInRoom(doc1, ownerId));
        assertFalse(documentRoomService.isUserInRoom(doc2, ownerId));
        assertTrue(documentRoomService.getDocumentsForUser(ownerId).isEmpty());
    }

    @Test
    @DisplayName("Get users in room")
    void testGetUsersInRoom() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");
        documentRoomService.addUserToRoom(documentId, collaboratorId, "Collaborator");

        Set<Long> users = documentRoomService.getUsersInRoom(documentId);

        assertEquals(2, users.size());
        assertTrue(users.contains(ownerId));
        assertTrue(users.contains(collaboratorId));
    }

    @Test
    @DisplayName("Get users info in room")
    void testGetUsersInfoInRoom() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");
        documentRoomService.addUserToRoom(documentId, collaboratorId, "Collaborator");

        List<DocumentRoomService.UserInfo> usersInfo = documentRoomService.getUsersInfoInRoom(documentId);

        assertEquals(2, usersInfo.size());
        assertTrue(usersInfo.stream().anyMatch(u -> u.getUserId().equals(ownerId) && u.getUserName().equals("Owner")));
        assertTrue(usersInfo.stream().anyMatch(u -> u.getUserId().equals(collaboratorId) && u.getUserName().equals("Collaborator")));
    }

    @Test
    @DisplayName("Get users info in empty room")
    void testGetUsersInfoInRoom_Empty() {
        List<DocumentRoomService.UserInfo> usersInfo = documentRoomService.getUsersInfoInRoom(documentId);

        assertTrue(usersInfo.isEmpty());
    }

    @Test
    @DisplayName("Check if user is in room")
    void testIsUserInRoom() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");

        assertTrue(documentRoomService.isUserInRoom(documentId, ownerId));
        assertFalse(documentRoomService.isUserInRoom(documentId, collaboratorId));
    }

    @Test
    @DisplayName("Get room user count")
    void testGetRoomUserCount() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(documentId, collaboratorId)).thenReturn(true);

        assertEquals(0, documentRoomService.getRoomUserCount(documentId));
        
        documentRoomService.addUserToRoom(documentId, ownerId, "Owner");
        assertEquals(1, documentRoomService.getRoomUserCount(documentId));
        
        documentRoomService.addUserToRoom(documentId, collaboratorId, "Collaborator");
        assertEquals(2, documentRoomService.getRoomUserCount(documentId));
        
        documentRoomService.removeUserFromRoom(documentId, ownerId);
        assertEquals(1, documentRoomService.getRoomUserCount(documentId));
    }

    @Test
    @DisplayName("Get documents for user")
    void testGetDocumentsForUser() {
        Long doc1 = 1L;
        Long doc2 = 2L;
        
        when(documentService.canUserEditDocument(doc1, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(doc2, ownerId)).thenReturn(true);

        documentRoomService.addUserToRoom(doc1, ownerId, "Owner");
        documentRoomService.addUserToRoom(doc2, ownerId, "Owner");

        Set<Long> documents = documentRoomService.getDocumentsForUser(ownerId);

        assertEquals(2, documents.size());
        assertTrue(documents.contains(doc1));
        assertTrue(documents.contains(doc2));
    }

    @Test
    @DisplayName("Get documents for user not in any room")
    void testGetDocumentsForUser_NotInAnyRoom() {
        Set<Long> documents = documentRoomService.getDocumentsForUser(ownerId);

        assertTrue(documents.isEmpty());
    }

    @Test
    @DisplayName("Handle exception when checking permission")
    void testCanUserJoinRoom_Exception() {
        when(documentService.canUserEditDocument(documentId, ownerId))
            .thenThrow(new RuntimeException("Database error"));

        boolean result = documentRoomService.canUserJoinRoom(documentId, ownerId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Add same user twice - should only add once")
    void testAddUserToRoom_Duplicate() {
        when(documentService.canUserEditDocument(documentId, ownerId)).thenReturn(true);

        boolean result1 = documentRoomService.addUserToRoom(documentId, ownerId, "Owner");
        boolean result2 = documentRoomService.addUserToRoom(documentId, ownerId, "Owner");

        assertTrue(result1);
        assertTrue(result2); // Should still return true
        assertEquals(1, documentRoomService.getRoomUserCount(documentId));
    }

    @Test
    @DisplayName("Remove user not in room - should handle gracefully")
    void testRemoveUserFromRoom_NotInRoom() {
        documentRoomService.removeUserFromRoom(documentId, ownerId);

        // Should not throw exception
        assertFalse(documentRoomService.isUserInRoom(documentId, ownerId));
    }

    @Test
    @DisplayName("Multiple documents with same user")
    void testMultipleDocuments_SameUser() {
        Long doc1 = 1L;
        Long doc2 = 2L;
        
        when(documentService.canUserEditDocument(doc1, ownerId)).thenReturn(true);
        when(documentService.canUserEditDocument(doc2, ownerId)).thenReturn(true);

        documentRoomService.addUserToRoom(doc1, ownerId, "Owner");
        documentRoomService.addUserToRoom(doc2, ownerId, "Owner");

        assertTrue(documentRoomService.isUserInRoom(doc1, ownerId));
        assertTrue(documentRoomService.isUserInRoom(doc2, ownerId));
        assertEquals(2, documentRoomService.getDocumentsForUser(ownerId).size());
    }
}

