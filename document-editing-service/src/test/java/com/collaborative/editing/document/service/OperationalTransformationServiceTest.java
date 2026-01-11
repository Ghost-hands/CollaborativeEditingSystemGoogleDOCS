package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.OperationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Operational Transformation Service Tests")
class OperationalTransformationServiceTest {

    @InjectMocks
    private OperationalTransformationService otService;

    private Long userId1;
    private Long userId2;
    private Long documentId;

    @BeforeEach
    void setUp() {
        userId1 = 1L;
        userId2 = 2L;
        documentId = 1L;
    }

    // ========== Transform INSERT vs INSERT Tests ==========

    @Test
    @DisplayName("Transform INSERT before INSERT - should remain unchanged")
    void testTransformInsertInsert_Before() {
        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("B", 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("A", result.getContent());
        assertEquals(0, result.getPosition());
        assertEquals(userId1, result.getUserId());
    }

    @Test
    @DisplayName("Transform INSERT after INSERT - should shift position")
    void testTransformInsertInsert_After() {
        OperationDTO op1 = OperationDTO.insert("B", 5, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("A", 0, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("B", result.getContent());
        assertEquals(6, result.getPosition()); // 5 + 1 (length of "A")
        assertEquals(userId1, result.getUserId());
    }

    @Test
    @DisplayName("Transform INSERT at same position as INSERT - should remain unchanged")
    void testTransformInsertInsert_SamePosition() {
        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("B", 0, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("A", result.getContent());
        assertEquals(0, result.getPosition());
    }

    // ========== Transform INSERT vs DELETE Tests ==========

    @Test
    @DisplayName("Transform INSERT before DELETE - should remain unchanged")
    void testTransformInsertDelete_Before() {
        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(3, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("A", result.getContent());
        assertEquals(0, result.getPosition());
    }

    @Test
    @DisplayName("Transform INSERT after DELETE - should shift backward")
    void testTransformInsertDelete_After() {
        OperationDTO op1 = OperationDTO.insert("A", 10, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(3, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("A", result.getContent());
        assertEquals(7, result.getPosition()); // 10 - 3 (deleted length)
    }

    @Test
    @DisplayName("Transform INSERT within DELETE - should place at deletion start")
    void testTransformInsertDelete_Within() {
        OperationDTO op1 = OperationDTO.insert("A", 6, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(3, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("A", result.getContent());
        assertEquals(5, result.getPosition()); // Start of deletion
    }

    // ========== Transform DELETE vs INSERT Tests ==========

    @Test
    @DisplayName("Transform DELETE before INSERT - should remain unchanged")
    void testTransformDeleteInsert_Before() {
        OperationDTO op1 = OperationDTO.delete(3, 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("A", 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(3, result.getLength());
        assertEquals(0, result.getPosition());
    }

    @Test
    @DisplayName("Transform DELETE after INSERT - should shift forward")
    void testTransformDeleteInsert_After() {
        OperationDTO op1 = OperationDTO.delete(3, 10, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("A", 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(3, result.getLength());
        assertEquals(11, result.getPosition()); // 10 + 1 (length of "A")
    }

    @Test
    @DisplayName("Transform DELETE overlapping INSERT - should adjust length")
    void testTransformDeleteInsert_Overlapping() {
        OperationDTO op1 = OperationDTO.delete(5, 3, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("A", 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertTrue(result.getLength() < 5); // Length should be reduced
    }

    // ========== Transform DELETE vs DELETE Tests ==========

    @Test
    @DisplayName("Transform DELETE before DELETE - should remain unchanged")
    void testTransformDeleteDelete_Before() {
        OperationDTO op1 = OperationDTO.delete(3, 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(2, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(3, result.getLength());
        assertEquals(0, result.getPosition());
    }

    @Test
    @DisplayName("Transform DELETE after DELETE - should shift backward")
    void testTransformDeleteDelete_After() {
        OperationDTO op1 = OperationDTO.delete(3, 10, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(2, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(3, result.getLength());
        assertEquals(8, result.getPosition()); // 10 - 2 (deleted length)
    }

    @Test
    @DisplayName("Transform DELETE overlapping DELETE - should adjust")
    void testTransformDeleteDelete_Overlapping() {
        OperationDTO op1 = OperationDTO.delete(5, 3, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(3, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertTrue(result.getLength() < 5); // Length should be reduced
    }

    @Test
    @DisplayName("Transform DELETE completely within DELETE - should become RETAIN")
    void testTransformDeleteDelete_CompletelyWithin() {
        OperationDTO op1 = OperationDTO.delete(2, 6, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.delete(5, 5, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(OperationDTO.Type.RETAIN, result.getType());
        assertEquals(0, result.getLength());
    }

    // ========== Apply Operation Tests ==========

    @Test
    @DisplayName("Apply INSERT to empty document")
    void testApplyOperation_InsertToEmpty() {
        OperationDTO op = OperationDTO.insert("Hello", 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("", op);

        assertEquals("Hello", result);
    }

    @Test
    @DisplayName("Apply INSERT to non-empty document")
    void testApplyOperation_InsertToNonEmpty() {
        OperationDTO op = OperationDTO.insert("World", 5, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("HelloWorld", result);
    }

    @Test
    @DisplayName("Apply INSERT at position 0")
    void testApplyOperation_InsertAtStart() {
        OperationDTO op = OperationDTO.insert("Hi ", 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hi Hello", result);
    }

    @Test
    @DisplayName("Apply INSERT at end of document")
    void testApplyOperation_InsertAtEnd() {
        OperationDTO op = OperationDTO.insert(" World", 5, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("Apply INSERT with position beyond document length")
    void testApplyOperation_InsertBeyondLength() {
        OperationDTO op = OperationDTO.insert("!", 100, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hello!", result);
    }

    @Test
    @DisplayName("Apply DELETE to document")
    void testApplyOperation_Delete() {
        OperationDTO op = OperationDTO.delete(2, 3, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hel", result);
    }

    @Test
    @DisplayName("Apply DELETE at start of document")
    void testApplyOperation_DeleteAtStart() {
        OperationDTO op = OperationDTO.delete(2, 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("llo", result);
    }

    @Test
    @DisplayName("Apply DELETE at end of document")
    void testApplyOperation_DeleteAtEnd() {
        OperationDTO op = OperationDTO.delete(2, 3, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hel", result);
    }

    @Test
    @DisplayName("Apply DELETE beyond document length - should clamp")
    void testApplyOperation_DeleteBeyondLength() {
        OperationDTO op = OperationDTO.delete(10, 3, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hel", result); // Only deletes what's available
    }

    @Test
    @DisplayName("Apply DELETE to empty document - should return empty")
    void testApplyOperation_DeleteFromEmpty() {
        OperationDTO op = OperationDTO.delete(5, 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("", op);

        assertEquals("", result);
    }

    @Test
    @DisplayName("Apply INSERT to null document - should handle gracefully")
    void testApplyOperation_InsertToNull() {
        OperationDTO op = OperationDTO.insert("Hello", 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation(null, op);

        assertEquals("Hello", result);
    }

    @Test
    @DisplayName("Apply INSERT with null content - should return unchanged")
    void testApplyOperation_InsertWithNullContent() {
        OperationDTO op = OperationDTO.insert(null, 0, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hello", result);
    }

    // ========== Transform Against Operations Tests ==========

    @Test
    @DisplayName("Transform operation against empty concurrent operations list")
    void testTransformAgainstOperations_EmptyList() {
        OperationDTO op = OperationDTO.insert("A", 5, userId1, documentId, 1L, 0L);
        List<OperationDTO> concurrentOps = new ArrayList<>();

        OperationDTO result = otService.transformAgainstOperations(op, concurrentOps);

        assertEquals(op, result);
    }

    @Test
    @DisplayName("Transform operation against single concurrent operation")
    void testTransformAgainstOperations_SingleOperation() {
        OperationDTO op = OperationDTO.insert("B", 10, userId1, documentId, 1L, 0L);
        OperationDTO concurrentOp = OperationDTO.insert("A", 5, userId2, documentId, 2L, 0L);
        List<OperationDTO> concurrentOps = Arrays.asList(concurrentOp);

        OperationDTO result = otService.transformAgainstOperations(op, concurrentOps);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("B", result.getContent());
        assertEquals(11, result.getPosition()); // 10 + 1 (length of "A")
    }

    @Test
    @DisplayName("Transform operation against multiple concurrent operations")
    void testTransformAgainstOperations_MultipleOperations() {
        OperationDTO op = OperationDTO.insert("C", 20, userId1, documentId, 1L, 0L);
        OperationDTO op1 = OperationDTO.insert("A", 5, userId2, documentId, 2L, 0L);
        OperationDTO op2 = OperationDTO.insert("B", 10, userId2, documentId, 3L, 0L);
        List<OperationDTO> concurrentOps = Arrays.asList(op1, op2);

        OperationDTO result = otService.transformAgainstOperations(op, concurrentOps);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("C", result.getContent());
        // Should be transformed against both operations
        assertTrue(result.getPosition() > 20);
    }

    @Test
    @DisplayName("Transform operation against itself - should skip")
    void testTransformAgainstOperations_SameOperation() {
        OperationDTO op = OperationDTO.insert("A", 5, userId1, documentId, 1L, 0L);
        op.setOperationId(1L);
        OperationDTO sameOp = OperationDTO.insert("B", 10, userId1, documentId, 1L, 0L);
        sameOp.setOperationId(1L);
        List<OperationDTO> concurrentOps = Arrays.asList(sameOp);

        OperationDTO result = otService.transformAgainstOperations(op, concurrentOps);

        assertEquals(op, result); // Should remain unchanged
    }

    // ========== Create Operation From Change Tests ==========

    @Test
    @DisplayName("Create INSERT operation from content change")
    void testCreateOperationFromChange_Insert() {
        String oldContent = "Hello";
        String newContent = "Hello World";

        OperationDTO result = otService.createOperationFromChange(
            oldContent, newContent, 5, userId1, documentId, 0L);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals(" World", result.getContent());
        assertEquals(5, result.getPosition());
        assertEquals(userId1, result.getUserId());
        assertEquals(documentId, result.getDocumentId());
    }

    @Test
    @DisplayName("Create DELETE operation from content change")
    void testCreateOperationFromChange_Delete() {
        String oldContent = "Hello World";
        String newContent = "Hello";

        OperationDTO result = otService.createOperationFromChange(
            oldContent, newContent, 5, userId1, documentId, 0L);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(6, result.getLength()); // " World".length()
        assertEquals(5, result.getPosition());
    }

    @Test
    @DisplayName("Create operation from empty to non-empty")
    void testCreateOperationFromChange_EmptyToNonEmpty() {
        String oldContent = "";
        String newContent = "Hello";

        OperationDTO result = otService.createOperationFromChange(
            oldContent, newContent, 0, userId1, documentId, 0L);

        assertEquals(OperationDTO.Type.INSERT, result.getType());
        assertEquals("Hello", result.getContent());
        assertEquals(0, result.getPosition());
    }

    @Test
    @DisplayName("Create operation from non-empty to empty")
    void testCreateOperationFromChange_NonEmptyToEmpty() {
        String oldContent = "Hello";
        String newContent = "";

        OperationDTO result = otService.createOperationFromChange(
            oldContent, newContent, 0, userId1, documentId, 0L);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(5, result.getLength());
        assertEquals(0, result.getPosition());
    }

    @Test
    @DisplayName("Create operation from replacement (same length)")
    void testCreateOperationFromChange_Replacement() {
        String oldContent = "Hello";
        String newContent = "World";

        OperationDTO result = otService.createOperationFromChange(
            oldContent, newContent, 0, userId1, documentId, 0L);

        assertEquals(OperationDTO.Type.DELETE, result.getType());
        assertEquals(5, result.getLength());
    }

    // ========== Generate Operation ID Tests ==========

    @Test
    @DisplayName("Generate unique operation IDs")
    void testGenerateOperationId_Unique() {
        Long id1 = otService.generateOperationId();
        Long id2 = otService.generateOperationId();
        Long id3 = otService.generateOperationId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertTrue(id2 > id1);
        assertTrue(id3 > id2);
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    @DisplayName("Transform RETAIN operation - should return unchanged")
    void testTransform_RetainOperation() {
        OperationDTO op1 = OperationDTO.retain(5);
        OperationDTO op2 = OperationDTO.insert("A", 0, userId2, documentId, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(op1, result);
    }

    @Test
    @DisplayName("Transform operation against RETAIN - should return unchanged")
    void testTransform_AgainstRetain() {
        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.retain(5);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(op1, result);
    }

    @Test
    @DisplayName("Transform operations from different documents - should return unchanged")
    void testTransform_DifferentDocuments() {
        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, 1L, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("B", 0, userId2, 2L, 2L, 0L);

        OperationDTO result = otService.transform(op1, op2);

        assertEquals(op1, result);
    }

    @Test
    @DisplayName("Apply operation with negative position - should clamp to 0")
    void testApplyOperation_NegativePosition() {
        OperationDTO op = OperationDTO.insert("A", -5, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("AHello", result);
    }

    @Test
    @DisplayName("Apply DELETE with zero length - should return unchanged")
    void testApplyOperation_ZeroLengthDelete() {
        OperationDTO op = OperationDTO.delete(0, 2, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hello", result);
    }

    @Test
    @DisplayName("Apply DELETE with negative length - should return unchanged")
    void testApplyOperation_NegativeLengthDelete() {
        OperationDTO op = OperationDTO.delete(-5, 2, userId1, documentId, 1L, 0L);

        String result = otService.applyOperation("Hello", op);

        assertEquals("Hello", result);
    }

    // ========== Complex Scenarios ==========

    @Test
    @DisplayName("Complex scenario: Multiple concurrent inserts")
    void testComplex_MultipleConcurrentInserts() {
        // User 1 inserts "A" at position 0
        // User 2 inserts "B" at position 0
        // User 3 inserts "C" at position 0
        // All should be transformed correctly

        OperationDTO op1 = OperationDTO.insert("A", 0, userId1, documentId, 1L, 0L);
        OperationDTO op2 = OperationDTO.insert("B", 0, userId2, documentId, 2L, 0L);
        OperationDTO op3 = OperationDTO.insert("C", 0, userId2, documentId, 3L, 0L);

        // Transform op1 against op2 and op3
        List<OperationDTO> concurrentOps = Arrays.asList(op2, op3);
        OperationDTO transformed = otService.transformAgainstOperations(op1, concurrentOps);

        assertEquals(OperationDTO.Type.INSERT, transformed.getType());
        assertEquals("A", transformed.getContent());
        // Position should be shifted by both B and C
        assertEquals(2, transformed.getPosition());
    }

    @Test
    @DisplayName("Complex scenario: Insert and delete at same position")
    void testComplex_InsertAndDeleteSamePosition() {
        OperationDTO insertOp = OperationDTO.insert("X", 5, userId1, documentId, 1L, 0L);
        OperationDTO deleteOp = OperationDTO.delete(3, 5, userId2, documentId, 2L, 0L);

        // Transform insert against delete
        OperationDTO transformed = otService.transform(insertOp, deleteOp);

        assertEquals(OperationDTO.Type.INSERT, transformed.getType());
        assertEquals("X", transformed.getContent());
        assertEquals(5, transformed.getPosition()); // Should be at deletion start
    }

    @Test
    @DisplayName("Complex scenario: Cascading transformations")
    void testComplex_CascadingTransformations() {
        // Simulate real-time editing scenario
        String document = "Hello";
        
        // Operation 1: Insert " World" at position 5
        OperationDTO op1 = OperationDTO.insert(" World", 5, userId1, documentId, 1L, 0L);
        String afterOp1 = otService.applyOperation(document, op1);
        assertEquals("Hello World", afterOp1);
        
        // Operation 2: Delete "Hello" (positions 0-4) - concurrent with op1
        OperationDTO op2 = OperationDTO.delete(5, 0, userId2, documentId, 2L, 0L);
        
        // Transform op2 against op1
        OperationDTO transformedOp2 = otService.transform(op2, op1);
        
        // Apply transformed op2
        String afterOp2 = otService.applyOperation(afterOp1, transformedOp2);
        
        // Should result in " World"
        assertEquals(" World", afterOp2);
    }
}

