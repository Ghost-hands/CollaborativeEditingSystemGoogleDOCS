/**
 * Operation class representing a document edit operation
 * 
 * OT allows concurrent changes to a shared document by multiple users while ensuring:
 * - Eventual convergence: All users converge to the same state
 * - Intention preservation: Keeps user expectations intact
 * - Real-time responsiveness: Edits visible within milliseconds
 * - Causality tracking: Ensures edits are applied in meaningful order
 */
export class Operation {
  constructor(type, content, length, position, userId, documentId, operationId, baseVersion) {
    this.type = type // 'INSERT', 'DELETE', or 'RETAIN'
    this.content = content // For INSERT operations
    this.length = length // For DELETE operations
    this.position = position // Position in document
    this.userId = userId
    this.documentId = documentId
    this.operationId = operationId
    this.baseVersion = baseVersion || 0
  }

  static insert(content, position, userId, documentId, operationId, baseVersion) {
    return new Operation('INSERT', content, null, position, userId, documentId, operationId, baseVersion)
  }

  static delete(length, position, userId, documentId, operationId, baseVersion) {
    return new Operation('DELETE', null, length, position, userId, documentId, operationId, baseVersion)
  }

  static retain(length) {
    return new Operation('RETAIN', null, length, 0, null, null, null, 0)
  }
}

/**
 * Operational Transformation (OT) Engine
 * 
 * Components:
 * 1. Local Buffers: Temporarily store unsynced edits (handled by DocumentEditor component)
 * 2. Transformation Engine: Transforms remote ops against local context (this class)
 * 3. Server: Orders ops and manages state convergence (handled by backend)
 * 
 * How OT Works:
 * 1. Each user edits a local copy of the document
 * 2. Operations (e.g., insert "A" at position 5) are sent to a central server
 * 3. The server serializes operations and broadcasts transformed versions to other users
 * 4. Each client transforms incoming operations to preserve intent with their own pending changes
 * 
 * Example:
 * - User A: Insert("A", pos=0)
 * - User B: Insert("B", pos=0)
 * - Without OT: One write overwrites the other
 * - With OT: B's operation is transformed to Insert("B", pos=1)
 * - Final result: "AB" or "BA", based on order and transformation rules
 */
export class OperationalTransform {
  /**
   * Transform operation op1 against op2
   * Returns the transformed version of op1 that can be applied after op2
   * 
   * This is the core transformation function that preserves user intention
   * when operations are applied concurrently.
   */
  static transform(op1, op2) {
    if (op1.type === 'RETAIN' || op2.type === 'RETAIN') {
      return op1 // RETAIN operations don't need transformation
    }

    // If operations are from the same user or on different documents, no transformation needed
    if (op1.documentId != null && op2.documentId != null && op1.documentId !== op2.documentId) {
      return op1
    }

    // Case 1: Both are INSERT operations
    if (op1.type === 'INSERT' && op2.type === 'INSERT') {
      return this.transformInsertInsert(op1, op2)
    }
    
    // Case 2: op1 is INSERT, op2 is DELETE
    if (op1.type === 'INSERT' && op2.type === 'DELETE') {
      return this.transformInsertDelete(op1, op2)
    }
    
    // Case 3: op1 is DELETE, op2 is INSERT
    if (op1.type === 'DELETE' && op2.type === 'INSERT') {
      return this.transformDeleteInsert(op1, op2)
    }
    
    // Case 4: Both are DELETE operations
    if (op1.type === 'DELETE' && op2.type === 'DELETE') {
      return this.transformDeleteDelete(op1, op2)
    }
    
    return op1
  }

  /**
   * Transform INSERT against INSERT
   * 
   * Example from article:
   * - User A: Insert("A", pos=0)
   * - User B: Insert("B", pos=0)
   * - If A's operationId < B's operationId: A stays at pos=0, B transforms to pos=1
   * - Result: "AB"
   * - If B's operationId < A's operationId: B stays at pos=0, A transforms to pos=1
   * - Result: "BA"
   * 
   * This preserves intention: both users intended to insert at the beginning,
   * and both insertions are preserved in the final document.
   */
  static transformInsertInsert(op1, op2) {
    // If op1 inserts before op2, op1 stays the same
    if (op1.position < op2.position) {
      return op1 // op1 happens before op2, no change needed
    } else if (op1.position > op2.position) {
      // op1 inserts after op2, shift position forward by op2's content length
      return Operation.insert(op1.content, op1.position + op2.content.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    } else {
      // Same position - use operationId for tie-breaking (causality tracking)
      // Operation with lower operationId happened first (was created first)
      // 
      // Handle both server operationIds and client tempOperationIds
      // Server operations have operationId, client operations might have tempOperationId
      const op1Id = op1.operationId || op1.tempOperationId || 0
      const op2Id = op2.operationId || op2.tempOperationId || 0
      
      // If both have the same ID (both 0 or both null), use a deterministic tie-breaker
      // to ensure consistent transformation (use userId as secondary tie-breaker)
      if (op1Id === op2Id && op1Id === 0) {
        // Both operations have no ID - use userId as tie-breaker for consistency
        const userId1 = op1.userId || 0
        const userId2 = op2.userId || 0
        if (userId1 < userId2) {
          return op1
        } else {
          return Operation.insert(op1.content, op1.position + op2.content.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        }
      }
      
      if (op1Id < op2Id) {
        // op1 happened first - stays at same position
        return op1
      } else {
        // op2 happened first - shift op1 after op2's content
        return Operation.insert(op1.content, op1.position + op2.content.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
      }
    }
  }

  static transformInsertDelete(op1, op2) {
    // op1 is INSERT, op2 is DELETE
    // Handle same-position case with tie-breaking
    const pos1 = op1.position
    const pos2 = op2.position
    const len2 = op2.length
    
    if (pos1 < pos2) {
      // INSERT happens before DELETE - no change needed
      return op1
    } else if (pos1 > pos2 + len2) {
      // INSERT happens after DELETE - shift position backward by DELETE length
      return Operation.insert(op1.content, op1.position - op2.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    } else if (pos1 === pos2) {
      // Same position - use operationId for tie-breaking
      // Operation with lower operationId happened first
      const op1Id = op1.operationId || 0
      const op2Id = op2.operationId || 0
      
      if (op1Id < op2Id) {
        // INSERT happened first - stays at same position (DELETE deletes after it)
        return op1
      } else {
        // DELETE happened first - INSERT goes at the start of deletion (which is now empty)
        return Operation.insert(op1.content, op2.position, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
      }
    } else {
      // INSERT happens inside DELETE's deletion range - shift to start of deletion
      return Operation.insert(op1.content, op2.position, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    }
  }

  static transformDeleteInsert(op1, op2) {
    // op1 is DELETE, op2 is INSERT
    // When DELETE overlaps with INSERT, we need to preserve the DELETE operation
    // by adjusting it to delete content that exists AFTER the INSERT
    
    if (op1.position + op1.length <= op2.position) {
      // DELETE happens entirely before INSERT - no change needed
      return op1
    } else if (op1.position >= op2.position + op2.content.length) {
      // DELETE happens entirely after INSERT - shift position forward by INSERT length
      return Operation.delete(op1.length, op1.position + op2.content.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    } else {
      // DELETE overlaps with INSERT
      // Calculate what content still exists to delete after the INSERT
      
      if (op1.position < op2.position) {
        // DELETE starts before INSERT
        // The part before INSERT still exists, but the part overlapping with INSERT doesn't
        // We need to delete: [before INSERT] + [after INSERT if any]
        const beforeLength = op2.position - op1.position
        const afterStart = op2.position + op2.content.length
        const originalEnd = op1.position + op1.length
        const afterLength = originalEnd > afterStart ? originalEnd - afterStart : 0
        
        if (beforeLength > 0 && afterLength > 0) {
          // Both parts exist - we need to split into two operations, but for simplicity,
          // delete the part after INSERT (which is more likely to be what the user intended)
          return Operation.delete(afterLength, afterStart, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        } else if (beforeLength > 0) {
          // Only before part exists
          return Operation.delete(beforeLength, op1.position, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        } else if (afterLength > 0) {
          // Only after part exists
          return Operation.delete(afterLength, afterStart, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        } else {
          // All content to delete is within the INSERT - becomes zero-length (no-op)
          return Operation.delete(0, afterStart, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        }
      } else {
        // DELETE starts at or after INSERT start
        const insertEnd = op2.position + op2.content.length
        const deleteEnd = op1.position + op1.length
        
        if (op1.position === op2.position) {
          // Same position - use operationId for tie-breaking
          const op1Id = op1.operationId || 0
          const op2Id = op2.operationId || 0
          
          if (op1Id < op2Id) {
            // DELETE happened first - it deletes content that exists before INSERT
            // After INSERT, the DELETE should delete content after the INSERT
            if (deleteEnd > insertEnd) {
              const afterLength = deleteEnd - insertEnd
              return Operation.delete(afterLength, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
            } else {
              // DELETE is completely within INSERT - becomes zero-length
              return Operation.delete(0, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
            }
          } else {
            // INSERT happened first - DELETE deletes content after INSERT
            if (deleteEnd > insertEnd) {
              const afterLength = deleteEnd - insertEnd
              return Operation.delete(afterLength, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
            } else {
              // DELETE is completely within INSERT - becomes zero-length
              return Operation.delete(0, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
            }
          }
        } else if (op1.position >= insertEnd) {
          // DELETE starts after INSERT ends - just shift position forward
          return Operation.delete(op1.length, op1.position + op2.content.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        } else if (deleteEnd <= insertEnd) {
          // DELETE is completely within INSERT - becomes zero-length
          return Operation.delete(0, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        } else {
          // DELETE extends beyond INSERT - delete the part after INSERT
          const afterLength = deleteEnd - insertEnd
          return Operation.delete(afterLength, insertEnd, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
        }
      }
    }
  }

  static transformDeleteDelete(op1, op2) {
    // op1 is the operation being transformed, op2 is the operation it's being transformed against
    // After op2 is applied, we need to adjust op1 to account for op2's deletion
    
    const op1Start = op1.position
    const op1End = op1.position + op1.length
    const op2Start = op2.position
    const op2End = op2.position + op2.length
    
    if (op1End <= op2Start) {
      // op1 happens entirely before op2 - no change needed
      return op1
    } else if (op1Start >= op2End) {
      // op1 happens entirely after op2 - shift position backward by op2's length
      return Operation.delete(op1.length, op1.position - op2.length, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    } else {
      // op1 overlaps with op2 - calculate what part of op1's range still exists after op2
      // The parts that still exist are: before op2 (if op1 starts before op2) and after op2 (if op1 ends after op2)
      
      let resultStart = op1Start
      let resultLength = op1.length
      
      if (op1Start < op2Start) {
        // op1 starts before op2
        if (op1End <= op2End) {
          // op1 ends within op2 - only delete the part before op2
          resultLength = op2Start - op1Start
        } else {
          // op1 extends beyond op2 - delete before and after parts
          // For simplicity, delete the part after op2 (shifted by op2's deletion)
          resultStart = op2End - op2.length
          resultLength = op1End - op2End
        }
      } else {
        // op1 starts at or after op2 starts
        if (op1End <= op2End) {
          // op1 is completely within op2 - nothing left to delete
          resultLength = 0
          resultStart = op2Start - op2.length
        } else {
          // op1 starts within op2 but extends beyond - only delete the part after op2
          resultStart = op2End - op2.length
          resultLength = op1End - op2End
        }
      }
      
      // Don't convert DELETE to RETAIN - preserve DELETE type even if length is 0
      if (resultLength <= 0) {
        return Operation.delete(0, resultStart, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
      }
      
      return Operation.delete(resultLength, resultStart, op1.userId, op1.documentId, op1.operationId, op1.baseVersion)
    }
  }

  /**
   * Transform an operation against a list of concurrent operations
   * 
   * This is used by the client to transform incoming remote operations
   * against local pending operations (from the local buffer).
   * 
   * The server has already transformed the operation against OTHER users' operations,
   * but we still need to transform it against OUR OWN pending operations that haven't
   * been acknowledged yet. This ensures intention preservation.
   */
  static transformAgainstOperations(operation, concurrentOps) {
    let transformed = operation
    const operationId = operation.operationId
    
    for (const concurrentOp of concurrentOps) {
      const concurrentOpId = concurrentOp.operationId
      
      // Skip if it's the same operation (same operationId)
      if (operationId != null && concurrentOpId != null && operationId === concurrentOpId) {
        continue // Skip transforming against itself
      }
      
      // Transform against this concurrent operation
      transformed = this.transform(transformed, concurrentOp)
    }
    return transformed
  }

  /**
   * Apply an operation to a document state
   */
  static applyOperation(document, operation) {
    // Handle null or empty document
    if (!document) {
      document = ''
    }
    
    // Skip zero-length DELETE operations (they're no-ops from transformation)
    if (operation.type === 'DELETE') {
      const len = operation.length
      if (len != null && len === 0) {
        // Zero-length delete is a no-op, return document unchanged
        return document
      }
    }
    
    if (operation.type === 'INSERT') {
      // Validate and clamp position to ensure it's within valid bounds
      let pos = Number(operation.position)
      if (isNaN(pos) || pos < 0) {
        console.error('⚠️ Invalid INSERT position, clamping to 0:', { position: operation.position, operation })
        pos = 0
      }
      // Clamp position to document length (can insert at end, so <= length is valid)
      pos = Math.max(0, Math.min(pos, document.length))
      const content = operation.content || ''
      
      // Validate that content doesn't contain unexpected newlines that would cause issues
      // (This is just for debugging - we don't modify content, just log if suspicious)
      if (content.includes('\n') && content.length === 1) {
        console.warn('⚠️ INSERT operation contains single newline character - this might cause line breaks:', {
          position: pos,
          content: JSON.stringify(content),
          documentLength: document.length
        })
      }
      
      const result = document.substring(0, pos) + content + document.substring(pos)
      
      // Validate result makes sense
      if (result.length !== document.length + content.length) {
        console.error('❌ INSERT operation result length mismatch:', {
          expectedLength: document.length + content.length,
          actualLength: result.length,
          documentLength: document.length,
          contentLength: content.length,
          position: pos,
          operation
        })
      }
      
      return result
    } else if (operation.type === 'DELETE') {
      // Ensure position and length are valid numbers
      const pos = Math.max(0, Math.min(Number(operation.position) || 0, document.length))
      const len = Number(operation.length) || 0
      
      // For DELETE operations, always try to apply them even if slightly out of bounds
      // This handles cases where concurrent operations changed the document structure
      if (len <= 0) {
        console.warn('⚠️ DELETE operation with invalid length, ignoring:', { position: pos, length: len, operation })
        return document
      }
      
      // Handle out-of-bounds DELETE operations more gracefully
      // If position is beyond document, nothing to delete
      if (pos >= document.length) {
        console.warn('⚠️ DELETE operation position beyond document length, ignoring:', { 
          position: pos, 
          length: len, 
          documentLength: document.length, 
          operation 
        })
        return document
      }
      
      // Adjust length if it exceeds document bounds, but still apply the delete
      const maxDeleteLength = document.length - pos
      const actualDeleteLength = Math.min(len, maxDeleteLength)
      
      if (actualDeleteLength <= 0) {
        console.warn('⚠️ DELETE operation would delete nothing, ignoring:', { 
          position: pos, 
          requestedLength: len, 
          maxLength: maxDeleteLength,
          documentLength: document.length, 
          operation 
        })
        return document
      }
      
      // Apply the delete operation
      const result = document.substring(0, pos) + document.substring(pos + actualDeleteLength)
      
      // Log if we had to adjust the length
      if (actualDeleteLength < len) {
        console.warn('⚠️ DELETE operation length adjusted due to bounds:', {
          requestedLength: len,
          actualLength: actualDeleteLength,
          position: pos,
          documentLength: document.length
        })
      }
      
      return result
    }
    return document
  }

  /**
   * Create an operation from a text change
   * Improved delete detection to handle cases where user types then deletes
   */
  static createOperationFromChange(oldContent, newContent, cursorPosition, userId, documentId, baseVersion, operationId) {
    // Handle null/undefined
    if (!oldContent) oldContent = ''
    if (!newContent) newContent = ''
    
    // Handle empty document case explicitly
    if (oldContent.length === 0) {
      // Document is empty - any change is an insertion at position 0
      if (newContent.length > 0) {
        return Operation.insert(newContent, 0, userId, documentId, operationId, baseVersion)
      }
      // Both empty - no operation needed (shouldn't happen)
      return Operation.retain(0)
    }
    
    if (newContent.length === 0) {
      // New content is empty - delete everything
      return Operation.delete(oldContent.length, 0, userId, documentId, operationId, baseVersion)
    }
    
    // If lengths are the same but content differs, it's a replacement
    // This can happen when user selects text and types (delete + insert)
    // We'll treat it as a delete followed by insert, but return the delete first
    if (oldContent.length === newContent.length && oldContent !== newContent) {
      // Find the difference
      const minLen = Math.min(oldContent.length, newContent.length)
      let start = 0
      while (start < minLen && oldContent.charAt(start) === newContent.charAt(start)) {
        start++
      }
      let oldEnd = oldContent.length
      let newEnd = newContent.length
      while (oldEnd > start && newEnd > start && 
             oldContent.charAt(oldEnd - 1) === newContent.charAt(newEnd - 1)) {
        oldEnd--
        newEnd--
      }
      // This is a replacement - return delete operation
      const deletedLength = oldEnd - start
      return Operation.delete(deletedLength, start, userId, documentId, operationId, baseVersion)
    }
    
    // Improved diff algorithm for better delete detection
    // First, try to find the exact change point using cursor position if available
    if (cursorPosition != null && cursorPosition >= 0) {
      const cursorPos = Math.min(cursorPosition, oldContent.length)
      
      // Check if this is a simple single-character delete (most common case)
      if (oldContent.length === newContent.length + 1) {
        // Single character deleted - check around cursor position
        // For backspace: cursor is at position where char was deleted
        // For delete key: cursor is at position before deleted char
        if (cursorPos > 0 && oldContent.substring(0, cursorPos - 1) === newContent.substring(0, cursorPos - 1) &&
            oldContent.substring(cursorPos) === newContent.substring(cursorPos - 1)) {
          // Backspace at cursorPos
          return Operation.delete(1, cursorPos - 1, userId, documentId, operationId, baseVersion)
        }
        if (cursorPos < oldContent.length && oldContent.substring(0, cursorPos) === newContent.substring(0, cursorPos) &&
            oldContent.substring(cursorPos + 1) === newContent.substring(cursorPos)) {
          // Delete key at cursorPos
          return Operation.delete(1, cursorPos, userId, documentId, operationId, baseVersion)
        }
      }
      
      // Check for multiple character delete around cursor
      if (oldContent.length > newContent.length) {
        const deletedLength = oldContent.length - newContent.length
        // Try to find where the deletion happened relative to cursor
        // Check if deletion is before cursor (backspace)
        if (cursorPos >= deletedLength) {
          const beforeCursor = oldContent.substring(0, cursorPos - deletedLength)
          const afterCursor = oldContent.substring(cursorPos)
          if (beforeCursor + afterCursor === newContent) {
            // Deletion before cursor (backspace)
            return Operation.delete(deletedLength, cursorPos - deletedLength, userId, documentId, operationId, baseVersion)
          }
        }
        // Check if deletion is after cursor (delete key)
        if (cursorPos + deletedLength <= oldContent.length) {
          const beforeCursor = oldContent.substring(0, cursorPos)
          const afterCursor = oldContent.substring(cursorPos + deletedLength)
          if (beforeCursor + afterCursor === newContent) {
            // Deletion after cursor (delete key)
            return Operation.delete(deletedLength, cursorPos, userId, documentId, operationId, baseVersion)
          }
        }
      }
    }
    
    // Fallback to standard diff algorithm
    const minLen = Math.min(oldContent.length, newContent.length)
    let start = 0

    // Find start of difference
    while (start < minLen && oldContent.charAt(start) === newContent.charAt(start)) {
      start++
    }

    // Find end of difference from the end
    let oldEnd = oldContent.length
    let newEnd = newContent.length
    while (oldEnd > start && newEnd > start &&
           oldContent.charAt(oldEnd - 1) === newContent.charAt(oldEnd - 1)) {
      oldEnd--
      newEnd--
    }

    // Determine if it's an insert or delete
    if (newContent.length > oldContent.length) {
      // Insertion
      const insertedLength = newContent.length - oldContent.length
      
      // When cursor position is available, use it directly to extract only
      // the characters that were actually inserted. This fixes the issue where typing
      // in the middle of text causes the diff algorithm to incorrectly identify
      // the entire remaining text as inserted.
      let insertPosition = start
      let inserted = newContent.substring(start, newEnd)
      
      if (cursorPosition != null && cursorPosition >= 0) {
        const cursorPos = Math.max(0, Math.min(cursorPosition, oldContent.length))
        
        // Use cursor position to extract only what was actually inserted
        // The cursor position (after adjustment in handleContentChange) is where the
        // insertion happened. Extract only the insertedLength characters starting from
        // that position.
        if (cursorPos + insertedLength <= newContent.length && cursorPos <= oldContent.length) {
          const candidateInserted = newContent.substring(cursorPos, cursorPos + insertedLength)
          
          // Verify the extracted text makes sense by reconstructing the document
          // This catches cases where the diff algorithm incorrectly identified the insertion
          const beforeInsert = oldContent.substring(0, cursorPos)
          const afterInsert = oldContent.substring(cursorPos)
          const reconstructed = beforeInsert + candidateInserted + afterInsert
          
          // If reconstruction matches, we have the correct insertion
          if (reconstructed === newContent) {
            // Success! We correctly identified the insertion
            return Operation.insert(candidateInserted, cursorPos, userId, documentId, operationId, baseVersion)
          } else {
            // Reconstruction failed - cursor position might be wrong, try to find correct position
            // Try to find where the insertion actually happened by comparing strings
            // Look for the position where inserting insertedLength characters matches newContent
            for (let tryPos = Math.max(0, cursorPos - insertedLength); tryPos <= Math.min(oldContent.length, cursorPos + insertedLength); tryPos++) {
              const tryBefore = oldContent.substring(0, tryPos)
              const tryAfter = oldContent.substring(tryPos)
              const tryInserted = newContent.substring(tryPos, tryPos + insertedLength)
              const tryReconstructed = tryBefore + tryInserted + tryAfter
              
              if (tryReconstructed === newContent) {
                // Found the correct position!
                return Operation.insert(tryInserted, tryPos, userId, documentId, operationId, baseVersion)
              }
            }
            
            // If we still can't find it, fall back to diff algorithm
            console.warn('⚠️ Could not find correct insertion position using cursor, using diff algorithm result:', {
              cursorPos,
              insertedLength,
              diffStart: start,
              diffEnd: newEnd,
              oldContentPreview: oldContent.substring(Math.max(0, cursorPos - 5), Math.min(oldContent.length, cursorPos + 10)),
              newContentPreview: newContent.substring(Math.max(0, cursorPos - 5), Math.min(newContent.length, cursorPos + 10))
            })
          }
        } else {
          // Cursor position is out of bounds, use diff result
          console.warn('⚠️ Cursor position out of bounds, using diff result:', {
            cursorPos,
            insertedLength,
            oldContentLength: oldContent.length,
            newContentLength: newContent.length
          })
        }
      }
      
      // Fall back to diff algorithm result if cursor-based extraction failed
      return Operation.insert(inserted, insertPosition, userId, documentId, operationId, baseVersion)
    } else if (newContent.length < oldContent.length) {
      // Deletion - use cursor position to determine deletion point if available
      const deletedLength = oldEnd - start
      let deletePosition = start

      // If cursor position is available, use it to better determine deletion position
      // This helps with backspace/delete key presses
      if (cursorPosition != null && cursorPosition >= 0) {
        const cursorPos = Math.max(0, Math.min(cursorPosition, oldContent.length))
        // For backspace, cursor is typically at the deletion end
        // For delete key, cursor is typically at the deletion start
        // Check if cursor is near the detected deletion area
        if (cursorPos >= start && cursorPos <= oldEnd) {
          // Cursor is in the deletion range - use cursor position as hint
          // If cursor is closer to start, it's likely delete key (delete after cursor)
          // If cursor is closer to end, it's likely backspace (delete before cursor)
          if (cursorPos - start < oldEnd - cursorPos) {
            // Cursor closer to start - likely delete key
            deletePosition = cursorPos
          } else {
            // Cursor closer to end - likely backspace
            deletePosition = Math.max(0, cursorPos - deletedLength)
          }
        } else if (cursorPos < start) {
          // Cursor is before deletion - likely backspace at cursor
          deletePosition = Math.max(0, cursorPos)
        }
        // If cursor is after oldEnd, use detected start position
      }

      // Validate the delete operation
      // Ensure the delete position and length make sense
      if (deletePosition < 0 || deletePosition + deletedLength > oldContent.length) {
        console.warn('⚠️ Invalid delete position detected, using fallback:', {
          deletePosition,
          deletedLength,
          oldContentLength: oldContent.length,
          start,
          oldEnd
        })
        deletePosition = start
      }

      return Operation.delete(deletedLength, deletePosition, userId, documentId, operationId, baseVersion)
    } else {
      // Replacement (delete + insert) - return delete first
      const deletedLength = oldEnd - start
      return Operation.delete(deletedLength, start, userId, documentId, operationId, baseVersion)
    }
  }
}
