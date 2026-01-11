package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.CursorPositionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cursor Tracking Service Tests")
class CursorTrackingServiceTest {

    @InjectMocks
    private CursorTrackingService cursorTrackingService;

    private Long documentId;
    private Long userId1;
    private Long userId2;
    private String userName1;
    private String userName2;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId1 = 1L;
        userId2 = 2L;
        userName1 = "User1";
        userName2 = "User2";
    }

    @Test
    @DisplayName("Update cursor position for user")
    void testUpdateCursor() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(1, cursors.size());
        assertEquals(userId1, cursors.get(0).getUserId());
        assertEquals(documentId, cursors.get(0).getDocumentId());
        assertEquals(5, cursors.get(0).getPosition());
        assertEquals(userName1, cursors.get(0).getUserName());
        assertNotNull(cursors.get(0).getColor());
    }

    @Test
    @DisplayName("Update cursor position multiple times - should update")
    void testUpdateCursor_MultipleUpdates() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.updateCursor(documentId, userId1, 10, userName1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(1, cursors.size());
        assertEquals(10, cursors.get(0).getPosition());
    }

    @Test
    @DisplayName("Update cursor for multiple users")
    void testUpdateCursor_MultipleUsers() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.updateCursor(documentId, userId2, 10, userName2);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(2, cursors.size());
        assertTrue(cursors.stream().anyMatch(c -> c.getUserId().equals(userId1) && c.getPosition() == 5));
        assertTrue(cursors.stream().anyMatch(c -> c.getUserId().equals(userId2) && c.getPosition() == 10));
    }

    @Test
    @DisplayName("Remove cursor for user")
    void testRemoveCursor() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.removeCursor(documentId, userId1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertTrue(cursors.isEmpty());
    }

    @Test
    @DisplayName("Remove cursor for one user - other users remain")
    void testRemoveCursor_OneUser() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.updateCursor(documentId, userId2, 10, userName2);
        
        cursorTrackingService.removeCursor(documentId, userId1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(1, cursors.size());
        assertEquals(userId2, cursors.get(0).getUserId());
    }

    @Test
    @DisplayName("Get cursors for document with no cursors")
    void testGetCursorsForDocument_Empty() {
        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertTrue(cursors.isEmpty());
    }

    @Test
    @DisplayName("Get cursors for different documents")
    void testGetCursorsForDocument_DifferentDocuments() {
        Long doc1 = 1L;
        Long doc2 = 2L;
        
        cursorTrackingService.updateCursor(doc1, userId1, 5, userName1);
        cursorTrackingService.updateCursor(doc2, userId2, 10, userName2);

        List<CursorPositionDTO> cursors1 = cursorTrackingService.getCursorsForDocument(doc1);
        List<CursorPositionDTO> cursors2 = cursorTrackingService.getCursorsForDocument(doc2);
        
        assertEquals(1, cursors1.size());
        assertEquals(userId1, cursors1.get(0).getUserId());
        
        assertEquals(1, cursors2.size());
        assertEquals(userId2, cursors2.get(0).getUserId());
    }

    @Test
    @DisplayName("Remove cursor from non-existent document - should handle gracefully")
    void testRemoveCursor_NonExistentDocument() {
        cursorTrackingService.removeCursor(documentId, userId1);

        // Should not throw exception
        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        assertTrue(cursors.isEmpty());
    }

    @Test
    @DisplayName("Cursor color assignment - same user should get same color")
    void testCursorColor_Consistent() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        List<CursorPositionDTO> cursors1 = cursorTrackingService.getCursorsForDocument(documentId);
        String color1 = cursors1.get(0).getColor();

        cursorTrackingService.updateCursor(documentId, userId1, 10, userName1);
        List<CursorPositionDTO> cursors2 = cursorTrackingService.getCursorsForDocument(documentId);
        String color2 = cursors2.get(0).getColor();

        assertEquals(color1, color2);
    }

    @Test
    @DisplayName("Cursor color assignment - different users may get different colors")
    void testCursorColor_DifferentUsers() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.updateCursor(documentId, userId2, 10, userName2);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        String color1 = cursors.stream()
            .filter(c -> c.getUserId().equals(userId1))
            .findFirst()
            .map(CursorPositionDTO::getColor)
            .orElse(null);
        
        String color2 = cursors.stream()
            .filter(c -> c.getUserId().equals(userId2))
            .findFirst()
            .map(CursorPositionDTO::getColor)
            .orElse(null);
        
        assertNotNull(color1);
        assertNotNull(color2);
        // Colors may be same or different depending on userId modulo
    }

    @Test
    @DisplayName("Update cursor with null position")
    void testUpdateCursor_NullPosition() {
        cursorTrackingService.updateCursor(documentId, userId1, null, userName1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(1, cursors.size());
        assertNull(cursors.get(0).getPosition());
    }

    @Test
    @DisplayName("Update cursor with negative position")
    void testUpdateCursor_NegativePosition() {
        cursorTrackingService.updateCursor(documentId, userId1, -5, userName1);

        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        
        assertEquals(1, cursors.size());
        assertEquals(-5, cursors.get(0).getPosition());
    }

    @Test
    @DisplayName("Remove all cursors - document should be cleaned up")
    void testRemoveCursor_Cleanup() {
        cursorTrackingService.updateCursor(documentId, userId1, 5, userName1);
        cursorTrackingService.removeCursor(documentId, userId1);

        // Document should be removed from map when empty
        List<CursorPositionDTO> cursors = cursorTrackingService.getCursorsForDocument(documentId);
        assertTrue(cursors.isEmpty());
    }
}

