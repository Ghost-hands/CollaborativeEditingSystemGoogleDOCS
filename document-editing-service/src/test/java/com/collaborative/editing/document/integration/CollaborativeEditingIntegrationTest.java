package com.collaborative.editing.document.integration;

import com.collaborative.editing.common.dto.OperationDTO;
import com.collaborative.editing.document.service.OperationalTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("Collaborative Editing Integration Tests")
class CollaborativeEditingIntegrationTest {

    @Autowired
    private OperationalTransformationService otService;

    private Long documentId;
    private Long userId1;
    private Long userId2;
    private Long userId3;

    @BeforeEach
    void setUp() {
        documentId = 1L;
        userId1 = 1L;
        userId2 = 2L;
        userId3 = 3L;
    }

    @Test
    @DisplayName("Integration: Three users editing simultaneously - version 0 empty document")
    void testThreeUsersEditing_EmptyDocument() {
        // Start with empty document
        String document = "";
        
        // User 1 inserts "Hello" at position 0
        OperationDTO op1 = OperationDTO.insert("Hello", 0, userId1, documentId, 1L, 0L);
        String afterOp1 = otService.applyOperation(document, op1);
        assertEquals("Hello", afterOp1);
        
        // User 2 inserts "World" at position 0 (concurrent with op1)
        OperationDTO op2 = OperationDTO.insert("World", 0, userId2, documentId, 2L, 0L);
        
        // Transform op2 against op1
        OperationDTO transformedOp2 = otService.transform(op2, op1);
        String afterOp2 = otService.applyOperation(afterOp1, transformedOp2);
        
        // User 3 inserts "!" at position 0 (concurrent with op1 and op2)
        OperationDTO op3 = OperationDTO.insert("!", 0, userId3, documentId, 3L, 0L);
        
        // Transform op3 against op1 and op2
        List<OperationDTO> concurrentOps = Arrays.asList(op1, op2);
        OperationDTO transformedOp3 = otService.transformAgainstOperations(op3, concurrentOps);
        String afterOp3 = otService.applyOperation(afterOp2, transformedOp3);
        
        // Final result should contain all three inserts in correct order
        assertTrue(afterOp3.contains("Hello"));
        assertTrue(afterOp3.contains("World"));
        assertTrue(afterOp3.contains("!"));
    }

    @Test
    @DisplayName("Integration: Concurrent insert and delete operations")
    void testConcurrentInsertAndDelete() {
        String document = "Hello World";
        
        // User 1 deletes "Hello " (positions 0-5)
        OperationDTO deleteOp = OperationDTO.delete(6, 0, userId1, documentId, 1L, 0L);
        
        // User 2 inserts "Hi " at position 0 (concurrent)
        OperationDTO insertOp = OperationDTO.insert("Hi ", 0, userId2, documentId, 2L, 0L);
        
        // Apply delete first
        String afterDelete = otService.applyOperation(document, deleteOp);
        assertEquals("World", afterDelete);
        
        // Transform insert against delete
        OperationDTO transformedInsert = otService.transform(insertOp, deleteOp);
        String finalResult = otService.applyOperation(afterDelete, transformedInsert);
        
        assertEquals("Hi World", finalResult);
    }

    @Test
    @DisplayName("Integration: Multiple concurrent deletes")
    void testMultipleConcurrentDeletes() {
        String document = "Hello World Test";
        
        // User 1 deletes "Hello " (positions 0-5)
        OperationDTO delete1 = OperationDTO.delete(6, 0, userId1, documentId, 1L, 0L);
        
        // User 2 deletes "World " (positions 6-11) - concurrent
        OperationDTO delete2 = OperationDTO.delete(6, 6, userId2, documentId, 2L, 0L);
        
        // Transform delete2 against delete1
        OperationDTO transformedDelete2 = otService.transform(delete2, delete1);
        
        // Apply delete1
        String afterDelete1 = otService.applyOperation(document, delete1);
        assertEquals("World Test", afterDelete1);
        
        // Apply transformed delete2
        String finalResult = otService.applyOperation(afterDelete1, transformedDelete2);
        
        assertEquals("Test", finalResult);
    }

    @Test
    @DisplayName("Integration: Complex editing scenario with multiple operations")
    void testComplexEditingScenario() {
        String document = "The quick brown fox";
        
        // User 1: Insert "very " before "quick"
        OperationDTO op1 = OperationDTO.insert("very ", 4, userId1, documentId, 1L, 0L);
        
        // User 2: Delete "brown " (concurrent)
        OperationDTO op2 = OperationDTO.delete(6, 10, userId2, documentId, 2L, 0L);
        
        // User 3: Insert "lazy " before "fox" (concurrent)
        OperationDTO op3 = OperationDTO.insert("lazy ", 15, userId3, documentId, 3L, 0L);
        
        // Apply operations in order, transforming as needed
        String result = document;
        
        // Apply op1
        result = otService.applyOperation(result, op1);
        assertEquals("The very quick brown fox", result);
        
        // Transform and apply op2
        OperationDTO transformedOp2 = otService.transform(op2, op1);
        result = otService.applyOperation(result, transformedOp2);
        
        // Transform and apply op3
        List<OperationDTO> concurrentOps = Arrays.asList(op1, op2);
        OperationDTO transformedOp3 = otService.transformAgainstOperations(op3, concurrentOps);
        result = otService.applyOperation(result, transformedOp3);
        
        // Verify final result
        assertTrue(result.contains("very"));
        assertFalse(result.contains("brown"));
        assertTrue(result.contains("lazy"));
    }

    @Test
    @DisplayName("Integration: Real-time collaboration with version 0")
    void testRealTimeCollaboration_Version0() {
        // Simulate version 0 empty document scenario
        String document = "";
        Long baseVersion = 0L;
        
        // User 1 types first character
        OperationDTO op1 = OperationDTO.insert("H", 0, userId1, documentId, 1L, baseVersion);
        String result = otService.applyOperation(document, op1);
        assertEquals("H", result);
        
        // User 2 types at same time (concurrent)
        OperationDTO op2 = OperationDTO.insert("W", 0, userId2, documentId, 2L, baseVersion);
        
        // Transform op2 against op1
        OperationDTO transformedOp2 = otService.transform(op2, op1);
        result = otService.applyOperation(result, transformedOp2);
        
        // Both characters should be present
        assertTrue(result.contains("H"));
        assertTrue(result.contains("W"));
        assertEquals(2, result.length());
    }

    @Test
    @DisplayName("Integration: Cascading transformations with multiple users")
    void testCascadingTransformations() {
        String document = "ABC";
        
        // Create a chain of operations
        List<OperationDTO> operations = new ArrayList<>();
        operations.add(OperationDTO.insert("X", 0, userId1, documentId, 1L, 0L));
        operations.add(OperationDTO.insert("Y", 1, userId2, documentId, 2L, 0L));
        operations.add(OperationDTO.insert("Z", 2, userId3, documentId, 3L, 0L));
        
        String result = document;
        List<OperationDTO> appliedOps = new ArrayList<>();
        
        for (OperationDTO op : operations) {
            // Transform against all previously applied operations
            OperationDTO transformed = otService.transformAgainstOperations(op, appliedOps);
            result = otService.applyOperation(result, transformed);
            appliedOps.add(transformed);
        }
        
        // All inserts should be present
        assertTrue(result.contains("X"));
        assertTrue(result.contains("Y"));
        assertTrue(result.contains("Z"));
        assertTrue(result.contains("ABC"));
    }

    @Test
    @DisplayName("Integration: Operation transformation preserves document consistency")
    void testOperationTransformation_Consistency() {
        String document = "Hello";
        
        // User 1 deletes "e" (position 1)
        OperationDTO deleteOp = OperationDTO.delete(1, 1, userId1, documentId, 1L, 0L);
        
        // User 2 inserts "a" at position 1 (concurrent)
        OperationDTO insertOp = OperationDTO.insert("a", 1, userId2, documentId, 2L, 0L);
        
        // Apply delete first
        String afterDelete = otService.applyOperation(document, deleteOp);
        assertEquals("Hllo", afterDelete);
        
        // Transform insert against delete
        OperationDTO transformedInsert = otService.transform(insertOp, deleteOp);
        
        // Apply transformed insert
        String finalResult = otService.applyOperation(afterDelete, transformedInsert);
        
        // Should be "Hallo" (insert at position 1 after deletion)
        assertEquals("Hallo", finalResult);
    }

    @Test
    @DisplayName("Integration: Empty document to full document with concurrent edits")
    void testEmptyToFullDocument_ConcurrentEdits() {
        String document = "";
        
        // Multiple users start typing in empty document
        OperationDTO op1 = OperationDTO.insert("User1: ", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("User2: ", 0, userId2, documentId, 2L, 0L);
        OperationDTO op3 = OperationDTO.insert("User3: ", 0, userId3, documentId, 3L, 0L);
        
        // Apply with transformations
        String result = document;
        result = otService.applyOperation(result, op1);
        
        OperationDTO transformedOp2 = otService.transform(op2, op1);
        result = otService.applyOperation(result, transformedOp2);
        
        List<OperationDTO> concurrentOps = Arrays.asList(op1, op2);
        OperationDTO transformedOp3 = otService.transformAgainstOperations(op3, concurrentOps);
        result = otService.applyOperation(result, transformedOp3);
        
        // All users' text should be present
        assertTrue(result.contains("User1:"));
        assertTrue(result.contains("User2:"));
        assertTrue(result.contains("User3:"));
    }
}

