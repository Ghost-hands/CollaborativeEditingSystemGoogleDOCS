package com.collaborative.editing.document.service;

import com.collaborative.editing.common.dto.OperationDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


@Service
public class OperationalTransformationService {
    
    private final AtomicLong operationIdGenerator = new AtomicLong(1);

    /**
     * Transform operation op1 against op2
     * Returns the transformed version of op1 that can be applied after op2
     */
    public OperationDTO transform(OperationDTO op1, OperationDTO op2) {
        if (op1.getType() == OperationDTO.Type.RETAIN || op2.getType() == OperationDTO.Type.RETAIN) {
            return op1; // RETAIN operations don't need transformation
        }

        // If operations are from the same user or on different documents, no transformation needed
        if (op1.getDocumentId() != null && op2.getDocumentId() != null && 
            !op1.getDocumentId().equals(op2.getDocumentId())) {
            return op1;
        }

        // Case 1: Both are INSERT operations
        if (op1.getType() == OperationDTO.Type.INSERT && op2.getType() == OperationDTO.Type.INSERT) {
            return transformInsertInsert(op1, op2);
        }
        
        // Case 2: op1 is INSERT, op2 is DELETE
        if (op1.getType() == OperationDTO.Type.INSERT && op2.getType() == OperationDTO.Type.DELETE) {
            return transformInsertDelete(op1, op2);
        }
        
        // Case 3: op1 is DELETE, op2 is INSERT
        if (op1.getType() == OperationDTO.Type.DELETE && op2.getType() == OperationDTO.Type.INSERT) {
            return transformDeleteInsert(op1, op2);
        }
        
        // Case 4: Both are DELETE operations
        if (op1.getType() == OperationDTO.Type.DELETE && op2.getType() == OperationDTO.Type.DELETE) {
            return transformDeleteDelete(op1, op2);
        }

        return op1;
    }

    /**
     * Transform INSERT against INSERT:
     * - User A: Insert("A", pos=0)
     * - User B: Insert("B", pos=0)
     * - If A's operationId < B's operationId: A stays at pos=0, B transforms to pos=1
     * - Result: "AB"
     * - If B's operationId < A's operationId: B stays at pos=0, A transforms to pos=1
     * - Result: "BA"
     * 
     * This preserves intention: both users intended to insert at the beginning,
     * and both insertions are preserved in the final document.
     * 
     * If op1 inserts before op2, op1 stays the same
     * If op1 inserts after op2, op1's position is shifted
     * When positions are equal, use operationId for tie-breaking (causality tracking)
     * Operations with lower operationId happened first and should be inserted first
     */
    private OperationDTO transformInsertInsert(OperationDTO op1, OperationDTO op2) {
        int pos1 = op1.getPosition();
        int pos2 = op2.getPosition();
        
        if (pos1 < pos2) {
            // op1 inserts before op2 - no change needed
            return op1;
        } else if (pos1 > pos2) {
            // op1 inserts after op2 - shift its position forward
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.INSERT);
            transformed.setContent(op1.getContent());
            transformed.setPosition(pos1 + op2.getContent().length());
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        } else {
            // Same position - use operationId for tie-breaking (causality tracking)
            // Operation with lower operationId happened first (was created first)
            // If op1 happened first (lower ID), it stays at the same position
            // If op2 happened first (lower ID), op1 is shifted after op2's content
            Long op1Id = op1.getOperationId();
            Long op2Id = op2.getOperationId();
            
            if (op1Id != null && op2Id != null) {
                if (op1Id < op2Id) {
                    // op1 happened first - stays at same position
                    return op1;
                } else {
                    // op2 happened first - shift op1 after op2's content
                    OperationDTO transformed = new OperationDTO();
                    transformed.setType(OperationDTO.Type.INSERT);
                    transformed.setContent(op1.getContent());
                    transformed.setPosition(pos1 + op2.getContent().length());
                    transformed.setUserId(op1.getUserId());
                    transformed.setDocumentId(op1.getDocumentId());
                    transformed.setOperationId(op1.getOperationId());
                    transformed.setBaseVersion(op1.getBaseVersion());
                    return transformed;
                }
            } else if (op1Id != null) {
                // op1 has ID, op2 doesn't - op1 happened first (server-assigned ID)
                return op1;
            } else if (op2Id != null) {
                // op2 has ID, op1 doesn't - op2 happened first (server-assigned ID)
                OperationDTO transformed = new OperationDTO();
                transformed.setType(OperationDTO.Type.INSERT);
                transformed.setContent(op1.getContent());
                transformed.setPosition(pos1 + op2.getContent().length());
                transformed.setUserId(op1.getUserId());
                transformed.setDocumentId(op1.getDocumentId());
                transformed.setOperationId(op1.getOperationId());
                transformed.setBaseVersion(op1.getBaseVersion());
                return transformed;
            } else {
                // Both have null IDs - use userId as deterministic tie-breaker for consistency
                Long userId1 = op1.getUserId();
                Long userId2 = op2.getUserId();
                if (userId1 != null && userId2 != null && userId1 < userId2) {
                    return op1;
                } else {
                    // op2 happened first or same userId - shift op1 after op2's content
                    OperationDTO transformed = new OperationDTO();
                    transformed.setType(OperationDTO.Type.INSERT);
                    transformed.setContent(op1.getContent());
                    transformed.setPosition(pos1 + op2.getContent().length());
                    transformed.setUserId(op1.getUserId());
                    transformed.setDocumentId(op1.getDocumentId());
                    transformed.setOperationId(op1.getOperationId());
                    transformed.setBaseVersion(op1.getBaseVersion());
                    return transformed;
                }
            }
        }
    }

    /**
     * Transform INSERT against DELETE
     * If op1 inserts before the deleted region, no change
     * If op1 inserts after, shift position backward
     * Handle same-position case with operationId tie-breaking
     */
    private OperationDTO transformInsertDelete(OperationDTO op1, OperationDTO op2) {
        int pos1 = op1.getPosition();
        int pos2 = op2.getPosition();
        int len2 = op2.getLength();
        
        if (pos1 < pos2) {
            // op1 inserts before the deleted region
            return op1;
        } else if (pos1 > pos2 + len2) {
            // op1 inserts after the deleted region, shift backward
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.INSERT);
            transformed.setContent(op1.getContent());
            transformed.setPosition(pos1 - len2);
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        } else if (pos1 == pos2) {
            // Same position - use operationId for tie-breaking
            // Operation with lower operationId happened first
            Long op1Id = op1.getOperationId();
            Long op2Id = op2.getOperationId();
            
            if (op1Id != null && op2Id != null) {
                if (op1Id < op2Id) {
                    // INSERT happened first - stays at same position (DELETE deletes after it)
                    return op1;
                } else {
                    // DELETE happened first - INSERT goes at the start of deletion (which is now empty)
                    OperationDTO transformed = new OperationDTO();
                    transformed.setType(OperationDTO.Type.INSERT);
                    transformed.setContent(op1.getContent());
                    transformed.setPosition(pos2);
                    transformed.setUserId(op1.getUserId());
                    transformed.setDocumentId(op1.getDocumentId());
                    transformed.setOperationId(op1.getOperationId());
                    transformed.setBaseVersion(op1.getBaseVersion());
                    return transformed;
                }
            } else {
                // Fallback: if operationId is null, treat INSERT as happening first
                return op1;
            }
        } else {
            // op1 inserts within the deleted region, place it at the start of deletion
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.INSERT);
            transformed.setContent(op1.getContent());
            transformed.setPosition(pos2);
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        }
    }

    /**
     * Transform DELETE against INSERT
     * If op1 deletes before op2, no change
     * If op1 deletes after op2, shift position forward
     */
    private OperationDTO transformDeleteInsert(OperationDTO op1, OperationDTO op2) {
        int pos1 = op1.getPosition();
        int pos2 = op2.getPosition();
        int len1 = op1.getLength();
        
        if (pos1 + len1 <= pos2) {
            // op1 deletes entirely before op2
            return op1;
        } else if (pos1 >= pos2 + op2.getContent().length()) {
            // op1 deletes entirely after op2, shift forward
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.DELETE);
            transformed.setLength(len1);
            transformed.setPosition(pos1 + op2.getContent().length());
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
            } else {
                // op1 overlaps with op2, adjust the delete operation
                // When DELETE overlaps with INSERT, handle it correctly
                // The DELETE should delete content that exists BEFORE and AFTER the INSERT
                if (pos1 < pos2) {
                    // DELETE starts before INSERT
                    int beforeLength = pos2 - pos1;
                    int afterStart = pos2 + op2.getContent().length();
                    int originalEnd = pos1 + len1;
                    int afterEnd = originalEnd;
                    int afterLength = afterEnd > afterStart ? afterEnd - afterStart : 0;
                    
                    // We need to delete both the part before INSERT and after INSERT
                    // But since we can only return one operation, we'll delete the part that makes most sense
                    // If there's content after INSERT, delete that (shifted by INSERT length)
                    // Otherwise, delete the part before INSERT
                    if (afterLength > 0) {
                        // There's content after INSERT to delete - this is the most common case
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(afterLength);
                        transformed.setPosition(afterStart); // Position after INSERT
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    } else if (beforeLength > 0) {
                        // Only before part exists - delete that
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(beforeLength);
                        transformed.setPosition(pos1);
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    } else {
                        // All content to delete is within the INSERT - becomes zero-length
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(0);
                        transformed.setPosition(afterStart);
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    }
                } else {
                    // DELETE starts at or after INSERT start
                    // The DELETE overlaps with the INSERT
                    // Calculate what part of DELETE still exists after INSERT
                    int insertEnd = pos2 + op2.getContent().length();
                    int deleteEnd = pos1 + len1;
                    
                    if (pos1 == pos2) {
                        // Same position - use operationId for tie-breaking
                        Long op1Id = op1.getOperationId();
                        Long op2Id = op2.getOperationId();
                        
                        if (op1Id != null && op2Id != null) {
                            if (op1Id < op2Id) {
                                // DELETE happened first - it deletes content that exists before INSERT
                                // After INSERT, the DELETE should delete content after the INSERT
                                if (deleteEnd > insertEnd) {
                                    int afterLength = deleteEnd - insertEnd;
                                    OperationDTO transformed = new OperationDTO();
                                    transformed.setType(OperationDTO.Type.DELETE);
                                    transformed.setLength(afterLength);
                                    transformed.setPosition(insertEnd);
                                    transformed.setUserId(op1.getUserId());
                                    transformed.setDocumentId(op1.getDocumentId());
                                    transformed.setOperationId(op1.getOperationId());
                                    transformed.setBaseVersion(op1.getBaseVersion());
                                    return transformed;
                                } else {
                                    // DELETE is completely within INSERT - becomes zero-length
                                    OperationDTO transformed = new OperationDTO();
                                    transformed.setType(OperationDTO.Type.DELETE);
                                    transformed.setLength(0);
                                    transformed.setPosition(insertEnd);
                                    transformed.setUserId(op1.getUserId());
                                    transformed.setDocumentId(op1.getDocumentId());
                                    transformed.setOperationId(op1.getOperationId());
                                    transformed.setBaseVersion(op1.getBaseVersion());
                                    return transformed;
                                }
                            } else {
                                // INSERT happened first - DELETE deletes content after INSERT
                                if (deleteEnd > insertEnd) {
                                    int afterLength = deleteEnd - insertEnd;
                                    OperationDTO transformed = new OperationDTO();
                                    transformed.setType(OperationDTO.Type.DELETE);
                                    transformed.setLength(afterLength);
                                    transformed.setPosition(insertEnd);
                                    transformed.setUserId(op1.getUserId());
                                    transformed.setDocumentId(op1.getDocumentId());
                                    transformed.setOperationId(op1.getOperationId());
                                    transformed.setBaseVersion(op1.getBaseVersion());
                                    return transformed;
                                } else {
                                    // DELETE is completely within INSERT - becomes zero-length
                                    OperationDTO transformed = new OperationDTO();
                                    transformed.setType(OperationDTO.Type.DELETE);
                                    transformed.setLength(0);
                                    transformed.setPosition(insertEnd);
                                    transformed.setUserId(op1.getUserId());
                                    transformed.setDocumentId(op1.getDocumentId());
                                    transformed.setOperationId(op1.getOperationId());
                                    transformed.setBaseVersion(op1.getBaseVersion());
                                    return transformed;
                                }
                            }
                        } else {
                            // Fallback: if operationId is null, treat DELETE as happening first
                            if (deleteEnd > insertEnd) {
                                int afterLength = deleteEnd - insertEnd;
                                OperationDTO transformed = new OperationDTO();
                                transformed.setType(OperationDTO.Type.DELETE);
                                transformed.setLength(afterLength);
                                transformed.setPosition(insertEnd);
                                transformed.setUserId(op1.getUserId());
                                transformed.setDocumentId(op1.getDocumentId());
                                transformed.setOperationId(op1.getOperationId());
                                transformed.setBaseVersion(op1.getBaseVersion());
                                return transformed;
                            } else {
                                OperationDTO transformed = new OperationDTO();
                                transformed.setType(OperationDTO.Type.DELETE);
                                transformed.setLength(0);
                                transformed.setPosition(insertEnd);
                                transformed.setUserId(op1.getUserId());
                                transformed.setDocumentId(op1.getDocumentId());
                                transformed.setOperationId(op1.getOperationId());
                                transformed.setBaseVersion(op1.getBaseVersion());
                                return transformed;
                            }
                        }
                    } else if (pos1 >= insertEnd) {
                        // DELETE starts after INSERT ends - just shift position
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(len1);
                        transformed.setPosition(pos1 + op2.getContent().length());
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    } else if (deleteEnd <= insertEnd) {
                        // DELETE is completely within INSERT - becomes zero-length
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(0);
                        transformed.setPosition(insertEnd);
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    } else {
                        // DELETE extends beyond INSERT - delete the part after INSERT
                        int afterLength = deleteEnd - insertEnd;
                        OperationDTO transformed = new OperationDTO();
                        transformed.setType(OperationDTO.Type.DELETE);
                        transformed.setLength(afterLength);
                        transformed.setPosition(insertEnd); // Position after INSERT
                        transformed.setUserId(op1.getUserId());
                        transformed.setDocumentId(op1.getDocumentId());
                        transformed.setOperationId(op1.getOperationId());
                        transformed.setBaseVersion(op1.getBaseVersion());
                        return transformed;
                    }
                }
            }
    }

    /**
     * Transform DELETE against DELETE
     * Both operations delete from the document
     */
    private OperationDTO transformDeleteDelete(OperationDTO op1, OperationDTO op2) {
        int pos1 = op1.getPosition();
        int pos2 = op2.getPosition();
        int len1 = op1.getLength();
        int len2 = op2.getLength();
        
        // If op1 deletes entirely before op2
        if (pos1 + len1 <= pos2) {
            return op1;
        }
        
        // If op1 deletes entirely after op2
        if (pos1 >= pos2 + len2) {
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.DELETE);
            transformed.setLength(len1);
            transformed.setPosition(pos1 - len2);
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        }
        
        // Overlapping deletes - adjust op1
        int overlapStart = Math.max(pos1, pos2);
        int overlapEnd = Math.min(pos1 + len1, pos2 + len2);
        int overlapLength = overlapEnd - overlapStart;
        
        if (pos1 < pos2) {
            // op1 starts before op2
            int newLength = len1 - overlapLength;
            // Don't convert DELETE to RETAIN - preserve DELETE type even if length is 0
            if (newLength <= 0) {
                // Delete is completely overlapped - return zero-length delete to preserve operation type
                OperationDTO transformed = new OperationDTO();
                transformed.setType(OperationDTO.Type.DELETE);
                transformed.setLength(0);
                transformed.setPosition(pos1);
                transformed.setUserId(op1.getUserId());
                transformed.setDocumentId(op1.getDocumentId());
                transformed.setOperationId(op1.getOperationId());
                transformed.setBaseVersion(op1.getBaseVersion());
                return transformed;
            }
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.DELETE);
            transformed.setLength(newLength);
            transformed.setPosition(pos1);
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        } else {
            // op1 starts after or at op2
            int newLength = len1 - overlapLength;
            // Don't convert DELETE to RETAIN - preserve DELETE type even if length is 0
            if (newLength <= 0) {
                // Delete is completely overlapped - return zero-length delete to preserve operation type
                OperationDTO transformed = new OperationDTO();
                transformed.setType(OperationDTO.Type.DELETE);
                transformed.setLength(0);
                transformed.setPosition(pos2);
                transformed.setUserId(op1.getUserId());
                transformed.setDocumentId(op1.getDocumentId());
                transformed.setOperationId(op1.getOperationId());
                transformed.setBaseVersion(op1.getBaseVersion());
                return transformed;
            }
            OperationDTO transformed = new OperationDTO();
            transformed.setType(OperationDTO.Type.DELETE);
            transformed.setLength(newLength);
            transformed.setPosition(pos2);
            transformed.setUserId(op1.getUserId());
            transformed.setDocumentId(op1.getDocumentId());
            transformed.setOperationId(op1.getOperationId());
            transformed.setBaseVersion(op1.getBaseVersion());
            return transformed;
        }
    }

    /**
     * Apply an operation to a document state
     */
    public String applyOperation(String document, OperationDTO operation) {
        // Handle null or empty document
        if (document == null) {
            document = "";
        }
        
        // Skip zero-length DELETE operations (they're no-ops from transformation)
        if (operation.getType() == OperationDTO.Type.DELETE) {
            Integer len = operation.getLength();
            if (len != null && len == 0) {
                // Zero-length delete is a no-op, return document unchanged
                return document;
            }
        }
        
        if (operation.getType() == OperationDTO.Type.INSERT) {
            int pos = operation.getPosition();
            String content = operation.getContent();
            if (content == null) {
                return document; // Invalid operation, return unchanged
            }
            if (pos < 0) pos = 0;
            if (pos > document.length()) pos = document.length();
            // For empty documents, ensure insertion at position 0 works correctly
            return document.substring(0, pos) + content + document.substring(pos);
        } else if (operation.getType() == OperationDTO.Type.DELETE) {
            Integer pos = operation.getPosition();
            Integer len = operation.getLength();
            
            // Validate DELETE operation parameters
            if (len == null || len <= 0) {
                // Invalid delete operation - log and return unchanged
                System.err.println("⚠️ Invalid DELETE operation: length is null or <= 0: " + len);
                return document;
            }
            if (pos == null || pos < 0) {
                // Invalid delete operation - log and return unchanged
                System.err.println("⚠️ Invalid DELETE operation: position is null or < 0: " + pos);
                return document;
            }
            
            // Clamp position to valid range
            int adjustedPos = Math.max(0, pos);
            if (adjustedPos > document.length()) {
                // Position is beyond document - log and return unchanged
                System.err.println("⚠️ DELETE operation position out of bounds: pos=" + adjustedPos + ", docLength=" + document.length());
                return document;
            }
            
            // Adjust length if it would go beyond document end
            int adjustedLen = len;
            if (adjustedPos + adjustedLen > document.length()) {
                adjustedLen = document.length() - adjustedPos;
            }
            
            if (adjustedLen <= 0) {
                // Nothing to delete - log and return unchanged
                System.err.println("⚠️ DELETE operation would delete 0 characters: pos=" + adjustedPos + ", len=" + adjustedLen);
                return document;
            }
            
            String result = document.substring(0, adjustedPos) + document.substring(adjustedPos + adjustedLen);
            System.out.println("✅ Applied DELETE: pos=" + adjustedPos + ", len=" + adjustedLen + ", docLength: " + document.length() + " -> " + result.length());
            return result;
        }
        return document;
    }

    /**
     * Transform an operation against a list of concurrent operations
     * 
     * This is used by the server to transform incoming operations against
     * operations that have already been applied (concurrent operations).
     * 
     * The server serializes operations and ensures they are transformed in
     * the correct order to maintain eventual convergence and intention preservation.
     * 
     * Operations are sorted by operationId to ensure correct transformation order
     * (causality tracking).
     */
    public OperationDTO transformAgainstOperations(OperationDTO operation, List<OperationDTO> concurrentOps) {
        OperationDTO transformed = operation;
        Long operationId = operation.getOperationId();
        
        // Log transformation steps for debugging
        boolean isInsert = operation.getType() == OperationDTO.Type.INSERT;
        int originalPos = operation.getPosition();
        
        for (OperationDTO concurrentOp : concurrentOps) {
            Long concurrentOpId = concurrentOp.getOperationId();
            
            // Skip if operationId is null (shouldn't happen, but handle gracefully)
            // Skip if it's the same operation (same operationId)
            if (operationId != null && concurrentOpId != null && operationId.equals(concurrentOpId)) {
                continue; // Skip transforming against itself
            }
            
            // Also skip if both are null (edge case)
            if (operationId == null && concurrentOpId == null) {
                // If both are null, we can't distinguish them, so skip to avoid infinite loops
                continue;
            }
            
            // Transform against this concurrent operation
            int posBefore = transformed.getPosition();
            transformed = transform(transformed, concurrentOp);
            int posAfter = transformed.getPosition();
            
            // Log if position changed
            if (isInsert && posBefore != posAfter) {
                System.out.println(String.format("🔄 Transform step: opId=%d, pos %d -> %d (against opId=%d, type=%s, pos=%d)", 
                        operationId, posBefore, posAfter, concurrentOpId, concurrentOp.getType(), concurrentOp.getPosition()));
            }
        }
        
        if (isInsert && originalPos != transformed.getPosition()) {
            System.out.println(String.format("✅ Final transform: opId=%d, original pos=%d, final pos=%d, content=%s", 
                    operationId, originalPos, transformed.getPosition(), transformed.getContent()));
        }
        
        return transformed;
    }

    /**
     * Generate a unique operation ID
     */
    public Long generateOperationId() {
        return operationIdGenerator.getAndIncrement();
    }

    /**
     * Convert a text change to an operation
     */
    public OperationDTO createOperationFromChange(String oldContent, String newContent, 
                                                  Integer cursorPosition, Long userId, 
                                                  Long documentId, Long baseVersion) {
        Long operationId = generateOperationId();
        
        // Simple diff algorithm - find the first difference
        int minLen = Math.min(oldContent.length(), newContent.length());
        int start = 0;
        
        // Find start of difference
        while (start < minLen && oldContent.charAt(start) == newContent.charAt(start)) {
            start++;
        }
        
        // Find end of difference from the end
        int oldEnd = oldContent.length();
        int newEnd = newContent.length();
        while (oldEnd > start && newEnd > start && 
               oldContent.charAt(oldEnd - 1) == newContent.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        
        // Determine if it's an insert or delete
        if (newContent.length() > oldContent.length()) {
            // Insertion
            String inserted = newContent.substring(start, newEnd);
            return OperationDTO.insert(inserted, start, userId, documentId, operationId, baseVersion);
        } else if (newContent.length() < oldContent.length()) {
            // Deletion
            int deletedLength = oldEnd - start;
            return OperationDTO.delete(deletedLength, start, userId, documentId, operationId, baseVersion);
        } else {
            // Replacement (delete + insert)
            // For simplicity, we'll treat it as a delete followed by insert
            // In production, you might want to send both operations
            int deletedLength = oldEnd - start;
            // Return the delete operation first
            return OperationDTO.delete(deletedLength, start, userId, documentId, operationId, baseVersion);
        }
    }

}

