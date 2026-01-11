import React, { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { documentService } from '../../services/documentService'
import { versionService } from '../../services/versionService'
import { websocketService } from '../../services/websocketService'
import { authService } from '../../services/authService'
import { OperationalTransform, Operation } from '../../services/operationalTransform'
import './DocumentEditor.css'

// Component to render remote user cursor (Google Docs style)
const RemoteCursor = ({ cursor, textarea, content }) => {
  const [position, setPosition] = useState({ top: 0, left: 0 })
  
  useEffect(() => {
    if (!textarea || cursor.position === null || cursor.position === undefined) {
      return
    }
    
    // Calculate cursor position in textarea
    const updatePosition = () => {
      try {
        // Clamp cursor position to valid range
        const maxPosition = Math.max(0, content.length)
        const clampedPosition = Math.max(0, Math.min(cursor.position, maxPosition))
        
        const textBeforeCursor = content.substring(0, clampedPosition)
        const lines = textBeforeCursor.split('\n')
        const lineNumber = lines.length - 1
        const lineText = lines[lineNumber] || ''
        
        // Create a temporary span to measure text width
        const style = window.getComputedStyle(textarea)
        const canvas = document.createElement('canvas')
        const context = canvas.getContext('2d')
        const fontSize = parseFloat(style.fontSize) || 16
        const fontFamily = style.fontFamily || 'monospace'
        context.font = `${fontSize}px ${fontFamily}`
        
        const textWidth = context.measureText(lineText).width
        
        // Calculate position
        const lineHeight = parseFloat(style.lineHeight) || fontSize * 1.6
        const paddingTop = parseFloat(style.paddingTop) || 0
        const paddingLeft = parseFloat(style.paddingLeft) || 0
        const borderTop = parseFloat(style.borderTopWidth) || 0
        const borderLeft = parseFloat(style.borderLeftWidth) || 0
        
        let top = paddingTop + borderTop + (lineNumber * lineHeight)
        let left = paddingLeft + borderLeft + textWidth
        
        // Get textarea bounds
        const textareaWidth = textarea.clientWidth
        const textareaHeight = textarea.clientHeight
        
        // Clamp position to stay within textarea bounds
        const maxLeft = paddingLeft + borderLeft + textareaWidth - 2 // 2px for cursor width
        const maxTop = paddingTop + borderTop + textareaHeight - 20 // 20px for cursor height
        
        left = Math.max(paddingLeft + borderLeft, Math.min(left, maxLeft))
        top = Math.max(paddingTop + borderTop, Math.min(top, maxTop))
        
        setPosition({ top, left })
      } catch (error) {
        console.error('Error calculating cursor position:', error)
      }
    }
    
    updatePosition()
    
    // Update on scroll and resize
    const handleUpdate = () => updatePosition()
    textarea.addEventListener('scroll', handleUpdate)
    window.addEventListener('resize', handleUpdate)
    
    return () => {
      textarea.removeEventListener('scroll', handleUpdate)
      window.removeEventListener('resize', handleUpdate)
    }
  }, [cursor.position, content, textarea])
  
  if (cursor.position === null || cursor.position === undefined) return null

  return (
    <div
      className="remote-cursor"
      style={{
        position: 'absolute',
        top: `${position.top}px`,
        left: `${position.left}px`,
        backgroundColor: cursor.color,
        width: '2px',
        height: '20px',
        zIndex: 10,
        pointerEvents: 'none',
        animation: 'cursor-blink 1s infinite',
        marginTop: '1px',
        boxShadow: '0 0 2px rgba(0, 0, 0, 0.2)',
        transition: 'left 0.1s linear, top 0.1s linear'
      }}
    >
      <div
        className="cursor-label"
        style={{
          position: 'absolute',
          top: '-28px',
          left: '-25px',
          backgroundColor: cursor.color,
          color: 'white',
          padding: '3px 8px',
          borderRadius: '4px',
          fontSize: '11px',
          fontWeight: 'bold',
          whiteSpace: 'nowrap',
          opacity: 0.9,
          pointerEvents: 'none',
          boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)',
          zIndex: 11
        }}
      >
        {cursor.userName}
      </div>
    </div>
  )
}

const DocumentEditor = ({ user }) => {
  const { id } = useParams()
  const navigate = useNavigate()
  const editorRef = useRef(null)
  const textareaRef = useRef(null)
  const [document, setDocument] = useState(null)
  const [content, setContent] = useState('') // Only for initial load, not for real-time updates
  const [title, setTitle] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [activeUsers, setActiveUsers] = useState([])
  const [versionHistory, setVersionHistory] = useState([])
  const [showVersionHistory, setShowVersionHistory] = useState(false)
  const [showCollaborators, setShowCollaborators] = useState(false)
  const [collaboratorId, setCollaboratorId] = useState('')
  const [collaboratorUsernames, setCollaboratorUsernames] = useState({}) // Map of userId -> username
  const [saving, setSaving] = useState(false)
  const [remoteCursors, setRemoteCursors] = useState({}) // Map of userId -> cursor data
  const cursorUpdateTimeoutRef = useRef(null)
  const [showExportMenu, setShowExportMenu] = useState(false)
  const lastSaveRef = useRef(null)
  const saveTimeoutRef = useRef(null)
  const isUpdatingRef = useRef(false) // Flag to prevent blanking during updates
  const skipNextSyncRef = useRef(false) // Skip React sync after direct textarea update
  
  // LOCAL BUFFER: Temporarily store unsynced edits
  // The local buffer is one of the three key components of OT:
  // 1. Local Buffers: Temporarily store unsynced edits (this ref)
  // 2. Transformation Engine: Transforms remote ops against local context (operationalTransform.js)
  // 3. Server: Orders ops and manages state convergence (backend)
  //
  // Operations are stored here until the server acknowledges them.
  // When a remote operation arrives, we transform it against operations in this buffer
  // to preserve user intention.
  const pendingOperationsRef = useRef([]) // Operations waiting to be applied (LOCAL BUFFER)
  const documentVersionRef = useRef(0)
  const operationIdCounterRef = useRef(1)
  const lastContentRef = useRef('') // Track last known content for OT
  const lastVersionedContentRef = useRef('') // Track last content that was versioned
  const versionCreationTimeoutRef = useRef(null) // Debounce version creation
  const documentLoadedRef = useRef(false) // Track if document has been initially loaded
  const initialContentRef = useRef('') // Track initial content when document is loaded (for back button check)
  const lastVersionCreatedByRef = useRef(null) // Track who created the last version we know about

  useEffect(() => {
    loadDocument()
    
    // Create version when component unmounts (user exits editing screen)
    return () => {
      // Save document immediately before exit to prevent data loss
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current)
      }
      if (versionCreationTimeoutRef.current) {
        clearTimeout(versionCreationTimeoutRef.current)
      }
      
      // Save document content immediately (synchronously if possible)
      // This ensures content is persisted even if user exits quickly
      const currentContent = textareaRef.current?.value || lastContentRef.current || content || ''
      if (document && document.id && currentContent !== lastSaveRef.current) {
        // Try to save synchronously - use a promise but don't block
        documentService.updateDocument(document.id, user.id, currentContent)
          .then(() => {
            console.log('Document saved on exit')
            lastSaveRef.current = currentContent
          })
          .catch(err => {
            console.error('Failed to save document on exit:', err)
          })
      }
      
      // Create version when exiting if there are unversioned changes
      if (document && document.id && content !== lastVersionedContentRef.current) {
        // Call async but don't block - browser will wait a bit for pending promises
        createVersionOnExit().catch(err => {
          console.error('Error in createVersionOnExit:', err)
        })
      }
      
      websocketService.disconnect()
    }
  }, [id])

  useEffect(() => {
    if (document && document.id) {
      console.log('Connecting WebSocket for document:', document.id)
      connectWebSocket()
    }
    return () => {
      console.log('Cleaning up WebSocket connection')
      websocketService.disconnect()
      // Clear cursor update timeout
      if (cursorUpdateTimeoutRef.current) {
        clearTimeout(cursorUpdateTimeoutRef.current)
      }
    }
  }, [document?.id])

  // Update textarea value when document is loaded and component is rendered
  // This ensures the document content is displayed even if it loads after initial render
  useEffect(() => {
    // Only update after loading is complete and document is available
    if (!loading && document && document.id) {
      const initialContent = document.content != null ? String(document.content) : ''
      
      // Use requestAnimationFrame to ensure textarea is rendered
      requestAnimationFrame(() => {
        if (textareaRef.current) {
          const currentValue = textareaRef.current.value || ''
          
          console.log('🔍 useEffect checking textarea update:', {
            documentId: document.id,
            loading,
            contentLength: initialContent.length,
            currentValueLength: currentValue.length,
            contentMatches: currentValue === initialContent,
            textareaExists: !!textareaRef.current
          })
          
          // Always update if content doesn't match (handles initial load and content changes)
          if (currentValue !== initialContent) {
            const cursorPosition = textareaRef.current.selectionStart || 0
            const scrollTop = textareaRef.current.scrollTop || 0
            const scrollLeft = textareaRef.current.scrollLeft || 0
            
            textareaRef.current.value = initialContent
            lastContentRef.current = initialContent
            
            // Restore cursor position and scroll
            textareaRef.current.setSelectionRange(cursorPosition, cursorPosition)
            textareaRef.current.scrollTop = scrollTop
            textareaRef.current.scrollLeft = scrollLeft
            
            if (!documentLoadedRef.current) {
              console.log('✅ useEffect: Initialized textarea with document content, length:', initialContent.length)
              documentLoadedRef.current = true
            } else {
              console.log('✅ useEffect: Updated textarea with document content, length:', initialContent.length)
            }
          } else {
            console.log('ℹ️ useEffect: Textarea already has correct content')
            if (!documentLoadedRef.current) {
              documentLoadedRef.current = true
            }
          }
        } else {
          // If textarea not ready, try again after a short delay
          setTimeout(() => {
            if (textareaRef.current && !loading && document && document.id) {
              const initialContent = document.content != null ? String(document.content) : ''
              const currentValue = textareaRef.current.value || ''
              if (currentValue !== initialContent) {
                textareaRef.current.value = initialContent
                lastContentRef.current = initialContent
                documentLoadedRef.current = true
                console.log('✅ useEffect (delayed): Set textarea value, length:', initialContent.length)
              }
            }
          }, 100)
        }
      })
    }
    // Reset flag when document ID changes
    if (!document?.id) {
      documentLoadedRef.current = false
    }
  }, [document?.id, document?.content, loading])



  const loadDocument = async () => {
    try {
      setLoading(true)
      documentLoadedRef.current = false // Reset flag before loading
      const doc = await documentService.getDocumentById(id, user.id)
      
      console.log('📄 Raw document response from API:', {
        id: doc.id,
        title: doc.title,
        hasContent: doc.content !== undefined && doc.content !== null,
        contentType: typeof doc.content,
        contentLength: doc.content ? doc.content.length : 0,
        contentPreview: doc.content ? doc.content.substring(0, 100) : 'null/undefined',
        fullDoc: JSON.stringify(doc).substring(0, 500)
      })
      
      setDocument(doc)
      setTitle(doc.title)
      const initialContent = doc.content != null ? String(doc.content) : ''
      
      console.log('📝 Processed content:', {
        initialContentLength: initialContent.length,
        initialContentPreview: initialContent.substring(0, 100),
        isEmpty: initialContent === ''
      })
      
      // Initialize all content references with the same value
      setContent(initialContent)
      lastSaveRef.current = initialContent
      lastContentRef.current = initialContent
      lastVersionedContentRef.current = initialContent
      initialContentRef.current = initialContent // Track initial content for back button check
      
      // Fetch usernames for collaborators
      if (doc.collaboratorIds && doc.collaboratorIds.length > 0) {
        fetchCollaboratorUsernames(doc.collaboratorIds)
      }
      
      // Don't try to update textarea here - it's not rendered yet
      // The useEffect hook will handle updating it once loading is false
      
      // Load version history to get the latest versioned content
      // Ensure version 0 is properly handled
      try {
        const history = await versionService.getVersionHistory(id)
        if (history && history.length > 0) {
          // Get the latest version (first in the array since it's sorted desc)
          const latestVersion = history[0]
          lastVersionedContentRef.current = latestVersion.content || initialContent
          // Track who created the latest version
          lastVersionCreatedByRef.current = latestVersion.createdBy
          // Pre-load version history for display
          setVersionHistory(history)
        } else {
          // No version history yet - document is at version 0
          console.log('✅ No version history - document at version 0, real-time editing enabled')
          lastVersionCreatedByRef.current = null
        }
        // Always use version 0 as base for real-time operations
        // This ensures all users work from the same base and see the same content
        documentVersionRef.current = 0
        console.log('✅ Real-time editing enabled with baseVersion 0')
      } catch (err) {
        console.warn('Failed to load version history:', err)
        // On error, assume version 0 to allow real-time editing
        documentVersionRef.current = 0
        console.log('✅ Assumed version 0 due to error - real-time editing enabled')
      }
    } catch (err) {
      console.error('Error loading document:', err)
      // Check if it's a 403 Forbidden error
      if (err.response && err.response.status === 403) {
        // Redirect to 403 page
        navigate('/403', { state: { from: `/document/${id}` } })
        return
      }
      setError(err.response?.data?.error || err.message || 'Failed to load document')
    } finally {
      setLoading(false)
    }
  }

  const connectWebSocket = () => {
    if (!document || !document.id) {
      console.warn('Cannot connect WebSocket: document not loaded')
      return
    }

    // Ensure WebSocket is fully connected before allowing operations
    // This is especially important for version 0 empty documents
    console.log('🔌 Connecting WebSocket for document:', document.id, 'content length:', (document.content || '').length)
    
    try {
      websocketService.connect(
        document.id,
        user.id,
        user.username || user.name || `User ${user.id}`,
        // Legacy change handler (DISABLED - causes screen blanking)
        (change) => {
          console.log('Received legacy change (ignored, using operations instead):', change)
          // Don't process legacy changes - they cause screen blanking
          // Only use operations for real-time sync
        },
        // OT operation handler
        (data) => {
          console.log('🔔 Received operation data from WebSocket:', data)
          // ALWAYS process operations - they are the source of truth for real-time sync
          if (data && data.operation) {
            console.log('✅ Processing operation from WebSocket, operation:', data.operation)
            try {
              handleRemoteOperation(data)
            } catch (error) {
              console.error('❌ Error processing remote operation:', error, data)
            }
          } else if (data && data.content !== undefined && data.content !== null) {
            // Only use content if no operation is present (fallback)
            console.log('⚠️ Received content-only message (fallback mode)')
            const textarea = textareaRef.current
            const isUserTyping = textarea && document.activeElement === textarea
            if (!isUserTyping) {
              try {
                handleRemoteOperation(data)
              } catch (error) {
                console.error('❌ Error processing content-only message:', error, data)
              }
            }
          } else {
            console.warn('❌ Received invalid operation data:', data)
          }
        },
        // Cursor handler - display other users' cursors
        (cursor) => {
          if (cursor && cursor.userId !== user.id) {
            setRemoteCursors(prev => {
              const newCursors = { ...prev }
              if (cursor.position !== null && cursor.position !== undefined) {
                newCursors[cursor.userId] = {
                  userId: cursor.userId,
                  userName: cursor.userName || `User ${cursor.userId}`,
                  position: cursor.position,
                  color: cursor.color || '#FF6B6B'
                }
              } else {
                // Remove cursor if position is null
                delete newCursors[cursor.userId]
              }
              return newCursors
            })
          }
        },
        // User join/leave event handler
        (userEvent) => {
          console.log('User event in document room:', userEvent)
          if (userEvent.type === 'user_joined') {
            const userName = userEvent.userName || `User ${userEvent.userId}`
            console.log(`User ${userName} (${userEvent.userId}) joined the document`)
            // Add user to active users list
            setActiveUsers(prev => {
              const exists = prev.find(u => u.userId === userEvent.userId)
              if (!exists) {
                return [...prev, { userId: userEvent.userId, userName }]
              }
              return prev
            })
            // Show notification
            setSuccess(`${userName} joined the document`)
            setTimeout(() => setSuccess(''), 3000)
          } else if (userEvent.type === 'user_left') {
            const userName = userEvent.userName || `User ${userEvent.userId}`
            console.log(`User ${userName} (${userEvent.userId}) left the document`)
            // Remove user from active users list
            setActiveUsers(prev => prev.filter(u => u.userId !== userEvent.userId))
            // Show notification
            setSuccess(`${userName} left the document`)
            setTimeout(() => setSuccess(''), 3000)
          } else if (userEvent.type === 'users_list') {
            console.log(`Users in document room: ${userEvent.users?.length || 0}`)
            // Update active users list from server
            if (userEvent.users && Array.isArray(userEvent.users)) {
              const users = userEvent.users.map(u => ({
                userId: u.userId || u.user_id,
                userName: u.userName || u.user_name || `User ${u.userId || u.user_id}`
              }))
              setActiveUsers(users.filter(u => u.userId !== user.id)) // Exclude current user
            }
          }
        }
      )
    } catch (error) {
      console.error('Error connecting WebSocket:', error)
      // Retry connection after a delay
      setTimeout(() => {
        if (document && document.id) {
          connectWebSocket()
        }
      }, 3000)
    }
  }

  const handleRemoteOperation = (messageData) => {
    try {
      console.log('Handling remote operation:', messageData)
      
      const textarea = textareaRef.current
      if (!textarea) {
        console.warn('Textarea not available, skipping operation')
        return
      }
      
      // Ignore if we're currently updating (prevents race conditions)
      if (isUpdatingRef.current) {
        console.log('⚠️ Ignoring remote operation - update in progress')
        // Queue it for later processing
        setTimeout(() => handleRemoteOperation(messageData), 10)
        return
      }
      
      const isUserTyping = window.document.activeElement === textarea
      // Use lastContentRef as the base content for applying remote operations
      // This ensures we're working with the document state that includes all our optimistically applied operations
      // The textarea value might be slightly out of sync due to React updates, so we use the ref as source of truth
      const textareaContent = textarea.value || ''
      const refContent = lastContentRef.current || ''
      
      // Use refContent as base, but if textarea differs significantly, use textarea and sync
      // This handles cases where ref might be stale
      let currentContent = refContent
      if (textareaContent && Math.abs(textareaContent.length - refContent.length) > 5) {
        console.warn('⚠️ Significant content desync detected, using textarea value:', {
          textareaLength: textareaContent.length,
          refLength: refContent.length,
          difference: textareaContent.length - refContent.length
        })
        currentContent = textareaContent
        lastContentRef.current = textareaContent
      } else if (textareaContent !== refContent && refContent.length > 0) {
        // Minor desync - prefer refContent but log it
        console.warn('⚠️ Minor content desync detected:', {
          textareaLength: textareaContent.length,
          refLength: refContent.length,
          difference: textareaContent.length - refContent.length
        })
        // Use refContent as it should be more accurate (includes all applied operations)
        currentContent = refContent
      } else if (!refContent && textareaContent) {
        // No ref content, use textarea
        currentContent = textareaContent
        lastContentRef.current = textareaContent
      }
      
      // Extract operation from message
      const op = messageData.operation
      if (!op) {
        console.warn('No operation in message, ignoring')
        return
      }
      
      // Handle both string and number types for userId comparison
      const remoteUserId = op.userId || messageData.userId
      const currentUserId = user.id
      // Convert both to strings for comparison to handle type mismatches
      const isOwnOperation = String(remoteUserId) === String(currentUserId)
      
      console.log('Operation check:', {
        remoteUserId,
        currentUserId,
        remoteUserIdType: typeof remoteUserId,
        currentUserIdType: typeof currentUserId,
        isOwnOperation
      })
      
      // PRIORITY 1: Apply operations immediately using OT (REAL-TIME)
      if (!isOwnOperation) {
        // This is a remote operation - transform it against our pending operations
        // Validate operation data
        if (!op.type) {
          console.error('❌ Invalid operation: missing type', op)
          return
        }
        
        // For DELETE operations, length and position MUST be valid numbers
        // For INSERT operations, content and position must be valid
        let operationLength = null
        let operationPosition = null
        
        if (op.type === 'DELETE') {
          // DELETE operations require length and position
          // Convert to numbers explicitly to handle string values from JSON
          operationLength = (op.length !== null && op.length !== undefined) ? 
            (typeof op.length === 'string' ? parseInt(op.length, 10) : Number(op.length)) : null
          operationPosition = (op.position !== null && op.position !== undefined) ? 
            (typeof op.position === 'string' ? parseInt(op.position, 10) : Number(op.position)) : null
          
          // Allow zero-length DELETE operations (they're no-ops from transformation)
          // But reject if length/position are null/undefined or position is negative
          if (operationLength === null || isNaN(operationLength) || 
              operationPosition === null || isNaN(operationPosition) || 
              operationPosition < 0) {
            console.error('❌ Invalid DELETE operation: missing or invalid length/position', {
              type: op.type,
              length: op.length,
              position: op.position,
              operationLength,
              operationPosition,
              operation: op
            })
            return
          }
          
          // Don't skip zero-length DELETE operations immediately
          // They might be valid if the content was already deleted by a concurrent operation
          // We'll let applyOperation handle them (it will skip them if they're truly no-ops)
          // But log it for debugging
          if (operationLength === 0) {
            console.log('⚠️ Received zero-length DELETE operation (will be handled by applyOperation):', {
              position: operationPosition,
              operationId: op.operationId,
              userId: op.userId || messageData.userId
            })
            // Continue processing - applyOperation will handle it correctly
          }
        } else if (op.type === 'INSERT') {
          // INSERT operations require content and position
          // Convert to number explicitly to handle string values from JSON
          operationPosition = (op.position !== null && op.position !== undefined) ? 
            (typeof op.position === 'string' ? parseInt(op.position, 10) : Number(op.position)) : null
          if (operationPosition === null || isNaN(operationPosition) || operationPosition < 0 || !op.content) {
            console.error('❌ Invalid INSERT operation: missing content or position', {
              type: op.type,
              content: op.content,
              position: op.position,
              operationPosition,
              operation: op
            })
            return
          }
        }
        
        const remoteOperation = new Operation(
          op.type,
          op.content || null,
          operationLength,
          operationPosition,
          op.userId || messageData.userId,
          op.documentId || messageData.documentId,
          op.operationId,
          op.baseVersion !== null && op.baseVersion !== undefined ? op.baseVersion : 0
        )
        
        console.log('📥 Received REMOTE operation from user:', remoteOperation.userId, 
                    'type:', remoteOperation.type, 'at position:', remoteOperation.position, 
                    'content:', remoteOperation.content || `delete ${remoteOperation.length} chars`,
                    'baseVersion:', remoteOperation.baseVersion)
        // Enhanced logging for DELETE operations to debug sync issues
        if (remoteOperation.type === 'DELETE') {
          console.log('🗑️ DELETE operation details:', {
            userId: remoteOperation.userId,
            position: remoteOperation.position,
            length: remoteOperation.length,
            operationId: remoteOperation.operationId,
            baseVersion: remoteOperation.baseVersion,
            currentContentLength: currentContent.length,
            willDeleteFrom: remoteOperation.position,
            willDeleteTo: remoteOperation.position + remoteOperation.length
          })
        }
        console.log('Current content length:', currentContent.length, 'Server version:', messageData.version, 'Pending ops:', pendingOperationsRef.current.length)
        
        // TRANSFORMATION ENGINE: Transform remote operation against local buffer
        // 
        // As described in the OT article, each client transforms incoming operations
        // to preserve intent with their own pending changes.
        //
        // The server has already transformed the operation against OTHER users' operations,
        // but we still need to transform it against OUR OWN pending operations that haven't
        // been acknowledged yet. This is because:
        // 1. Our pending operations have been applied optimistically to our local content
        // 2. The server's position is correct for the server's state (which doesn't include our pending ops)
        // 3. We need to adjust the remote operation's position to account for our pending ops
        //
        // Key: We only transform against operations that were created BEFORE the remote operation
        // (have lower operationId), because those represent local changes that the server hasn't seen yet.
        let transformedOp = remoteOperation
        
        // Get all unacknowledged pending operations from the LOCAL BUFFER (those with tempOperationId)
        // These represent local changes that haven't been reflected in the server's state yet
        // 
        // Transform against ALL unacknowledged operations (those with tempOperationId
        // but no server-assigned operationId). These represent local changes that the server hasn't
        // seen yet, so the server's position calculation doesn't account for them.
        //
        // The server has already transformed the remote operation against operations that happened
        // before it on the server. We need to transform against our local operations that the
        // server hasn't seen yet (unacknowledged operations).
        //
        // Note: We can't directly compare tempOperationId with server operationId because they're
        // in different number spaces. Instead, we transform against all unacknowledged operations,
        // which are guaranteed to have happened before the server processed the remote operation
        // (because they haven't been acknowledged yet).
        const unacknowledgedPendingOps = pendingOperationsRef.current.filter(pendingOp => {
          // A pending operation is unacknowledged if it has a tempOperationId but no server operationId
          // (meaning it was created locally and hasn't been acknowledged by the server yet)
          return pendingOp.tempOperationId != null && pendingOp.operationId == null
        })
        
        // Sort by operationId first (to ensure correct transformation order),
        // then by position to break ties
        unacknowledgedPendingOps.sort((a, b) => {
          const idA = a.operationId || a.tempOperationId || 0
          const idB = b.operationId || b.tempOperationId || 0
          if (idA !== idB) return idA - idB
          // Same ID - use position to break tie
          const posA = a.position || 0
          const posB = b.position || 0
          return posA - posB
        })
        
        // Transform the remote operation against each unacknowledged pending operation
        if (unacknowledgedPendingOps.length > 0) {
          console.log('🔄 Transforming remote operation (opId=' + (remoteOperation.operationId || 'none') + ') against', unacknowledgedPendingOps.length, 'unacknowledged local operations')
          for (const pendingOp of unacknowledgedPendingOps) {
            const beforeTransform = { 
              type: transformedOp.type,
              position: transformedOp.position,
              length: transformedOp.length,
              content: transformedOp.content
            }
            transformedOp = OperationalTransform.transform(transformedOp, pendingOp)
            if (beforeTransform.position !== transformedOp.position || 
                beforeTransform.length !== transformedOp.length ||
                beforeTransform.content !== transformedOp.content) {
              console.log('  Transformed:', {
                before: { pos: beforeTransform.position, len: beforeTransform.length, content: beforeTransform.content },
                after: { pos: transformedOp.position, len: transformedOp.length, content: transformedOp.content },
                against: { type: pendingOp.type, pos: pendingOp.position, len: pendingOp.length, content: pendingOp.content, opId: pendingOp.operationId || pendingOp.tempOperationId }
              })
            }
            // If transformation resulted in RETAIN, skip this operation
            if (transformedOp.type === 'RETAIN') {
              console.log('⚠️ Remote operation transformed to RETAIN (no-op), skipping')
              return
            }
          }
        }
        
        console.log('✅ Applying server-transformed remote operation directly:', {
          type: transformedOp.type,
          position: transformedOp.position,
          content: transformedOp.content || `delete ${transformedOp.length} chars`,
          operationId: transformedOp.operationId,
          currentContentLength: currentContent.length
        })
        
        // Validate position is within bounds
        // For INSERT: position can be 0 to document.length (inclusive - can insert at end)
        // For DELETE: position must be < document.length
        if (transformedOp.type === 'INSERT') {
          if (transformedOp.position < 0) {
            console.warn('⚠️ Remote INSERT position is negative, clamping to 0')
            transformedOp.position = 0
          } else if (transformedOp.position > currentContent.length) {
            // Position beyond document - might indicate state desync, clamp to end
            console.warn('⚠️ Remote INSERT position beyond document length (possible state desync), clamping to end:', {
              remotePosition: transformedOp.position,
              currentContentLength: currentContent.length
            })
            transformedOp.position = currentContent.length
          }
        } else if (transformedOp.type === 'DELETE') {
          if (transformedOp.position < 0) {
            console.warn('⚠️ Remote DELETE position is negative, clamping to 0')
            transformedOp.position = 0
          }
          if (transformedOp.position >= currentContent.length) {
            // Position at or beyond document - nothing to delete, skip operation
            console.warn('⚠️ Remote DELETE position at or beyond document length, skipping operation:', {
              remotePosition: transformedOp.position,
              currentContentLength: currentContent.length
            })
            return
          }
          // Validate that we can actually delete the requested length
          const maxDeleteLength = currentContent.length - transformedOp.position
          if (transformedOp.length > maxDeleteLength) {
            console.warn('⚠️ Remote DELETE length exceeds available content, adjusting:', {
              requestedLength: transformedOp.length,
              maxLength: maxDeleteLength,
              position: transformedOp.position
            })
            transformedOp.length = maxDeleteLength
            if (transformedOp.length <= 0) {
              // Nothing to delete, skip
              return
            }
          }
        }
        
        // Apply the transformed operation to current content IMMEDIATELY
        // ALWAYS apply remote operations, even if user is typing (real-time collaboration)
        console.log('Applying operation:', {
          type: transformedOp.type,
          position: transformedOp.position,
          length: transformedOp.length,
          content: transformedOp.content,
          currentContentLength: currentContent.length,
          contentPreview: currentContent.substring(Math.max(0, transformedOp.position - 10), Math.min(currentContent.length, transformedOp.position + 10))
        })
        
        // Before applying, validate the position is within bounds
        // For INSERT operations, position can be at document.length (insert at end)
        // For DELETE operations, position must be < document.length
        let safePosition = transformedOp.position
        if (transformedOp.type === 'INSERT') {
          // INSERT can be at position 0 to document.length (inclusive)
          safePosition = Math.max(0, Math.min(transformedOp.position, currentContent.length))
        } else if (transformedOp.type === 'DELETE') {
          // DELETE must be at position < document.length
          safePosition = Math.max(0, Math.min(transformedOp.position, Math.max(0, currentContent.length - 1)))
        }
        
        if (safePosition !== transformedOp.position) {
          console.warn('⚠️ Operation position was out of bounds, corrected:', {
            originalPosition: transformedOp.position,
            correctedPosition: safePosition,
            currentContentLength: currentContent.length,
            operationType: transformedOp.type,
            operation: transformedOp
          })
          transformedOp.position = safePosition
        }
        
        const newContent = OperationalTransform.applyOperation(currentContent, transformedOp)
        
        // Validate the result makes sense
        if (transformedOp.type === 'INSERT') {
          const expectedLength = currentContent.length + (transformedOp.content?.length || 0)
          if (newContent.length !== expectedLength) {
            console.error('❌ INSERT operation result length mismatch:', {
              expectedLength,
              actualLength: newContent.length,
              currentContentLength: currentContent.length,
              contentLength: transformedOp.content?.length || 0,
              position: transformedOp.position,
              operation: transformedOp,
              contentPreview: currentContent.substring(Math.max(0, transformedOp.position - 5), Math.min(currentContent.length, transformedOp.position + 5))
            })
            // Log the error but continue - the operation was still applied
            console.error('❌ INSERT operation length mismatch - operation may have been applied incorrectly')
          }
        }
        
        console.log('Content after operation: length', newContent.length, 'changed:', newContent !== currentContent, 
                    'operation type:', transformedOp.type)
        // Enhanced logging for DELETE operations to verify they're being applied
        if (transformedOp.type === 'DELETE') {
          console.log('✅ DELETE operation applied:', {
            beforeLength: currentContent.length,
            afterLength: newContent.length,
            deletedChars: currentContent.length - newContent.length,
            expectedDelete: transformedOp.length,
            position: transformedOp.position,
            match: (currentContent.length - newContent.length) === transformedOp.length
          })
        }
        
        // For DELETE operations, ALWAYS apply them even if content appears unchanged
        // This is because the DELETE might be valid but the content to delete doesn't exist at that position
        // in our current state (due to concurrent operations), but we should still update our state
        // Also, always apply if content changed OR if it's a DELETE operation (even if no change)
        const shouldApply = newContent !== currentContent || transformedOp.type === 'DELETE'
        
        if (shouldApply) {
          // For DELETE operations, verify they actually deleted something
          if (transformedOp.type === 'DELETE' && newContent === currentContent) {
            // DELETE didn't change content - this might mean the content to delete doesn't exist
            // at that position, but we should still update refs to maintain sync
            console.warn('⚠️ DELETE operation resulted in no content change - content may have been already deleted:', {
              position: transformedOp.position,
              length: transformedOp.length,
              currentContentLength: currentContent.length
            })
          }
          // Update textarea directly to prevent React re-render blanking
          // This is the key to real-time editing without screen blanking
          const cursorPosition = textarea.selectionStart
          const selectionEnd = textarea.selectionEnd
          const scrollTop = textarea.scrollTop
          const scrollLeft = textarea.scrollLeft
          
          // Set flag BEFORE updating to prevent handleContentChange from processing this
          isUpdatingRef.current = true
          
          // Update textarea directly (bypasses React, no blanking)
          textarea.value = newContent
          
          // Update refs IMMEDIATELY after updating textarea
          // This ensures lastContentRef is always in sync with textarea value
          lastContentRef.current = newContent
          lastSaveRef.current = newContent
          
          // Also update React state to keep it in sync
          // This is important for other parts of the component that rely on content state
          setContent(newContent)
          
          // Adjust cursor position based on operation
          let newCursorPos = cursorPosition
          let newSelectionEnd = selectionEnd
          
          if (transformedOp.type === 'INSERT' && transformedOp.position <= cursorPosition) {
            newCursorPos = cursorPosition + transformedOp.content.length
            newSelectionEnd = selectionEnd + transformedOp.content.length
          } else if (transformedOp.type === 'DELETE') {
            const deleteEnd = transformedOp.position + transformedOp.length
            if (deleteEnd <= cursorPosition) {
              newCursorPos = cursorPosition - transformedOp.length
              newSelectionEnd = Math.max(transformedOp.position, selectionEnd - transformedOp.length)
            } else if (transformedOp.position < cursorPosition) {
              newCursorPos = transformedOp.position
              newSelectionEnd = transformedOp.position
            }
          }
          
          newCursorPos = Math.max(0, Math.min(newCursorPos, newContent.length))
          newSelectionEnd = Math.max(0, Math.min(newSelectionEnd, newContent.length))
          
          // Restore cursor and scroll immediately
          textarea.setSelectionRange(newCursorPos, newSelectionEnd)
          textarea.scrollTop = scrollTop
          textarea.scrollLeft = scrollLeft
          
          // Reset the updating flag AFTER a small delay to ensure
          // any queued events from the textarea update are ignored
          setTimeout(() => {
            isUpdatingRef.current = false
          }, 0)
          
          if (newContent !== currentContent) {
            console.log('✅ Applied remote operation. Content length:', newContent.length, 'chars')
          } else {
            console.log('✅ Applied remote DELETE operation (no content change but operation processed)')
          }
        } else {
          // Even if content didn't change, update refs to ensure sync
          lastContentRef.current = newContent
          console.log('⚠️ Remote operation resulted in no content change (may be redundant)')
        }
        
        // Don't update documentVersionRef from WebSocket state version
        // The WebSocket state version is just an operation counter, not the actual document version
        // All users should work from the same base (version 0 for real-time editing)
        // The actual document version is only updated when a version is explicitly created
        
        // IGNORE server content - operations are the source of truth for real-time sync
      } else {
        // This is our own operation - find the matching pending operation and update it
        // Since server assigns new operationId, we match by:
        // 1. Type must match
        // 2. For INSERT: content must match exactly
        // 3. For DELETE: length must match exactly
        const pendingOp = pendingOperationsRef.current.find(p => {
          if (p.type !== op.type) return false
          if (p.type === 'INSERT') {
            return p.content === op.content
          } else if (p.type === 'DELETE') {
            return p.length === op.length
          }
          return false
        })
        
        if (pendingOp) {
          // Update with server's assigned operationId and transformed position
          const oldPosition = pendingOp.position
          pendingOp.operationId = op.operationId
          pendingOp.position = op.position
          pendingOp.length = op.length || null
          pendingOp.content = op.content || null
          console.log('✅ Received acknowledged operation:', {
            serverOperationId: op.operationId,
            serverPosition: op.position,
            ourOriginalPosition: oldPosition,
            type: op.type,
            positionChanged: oldPosition !== op.position
          })
        } else {
          console.warn('⚠️ Received own operation but could not find matching pending operation:', {
            type: op.type,
            position: op.position,
            content: op.content,
            length: op.length
          })
        }
        // Don't apply - it's already in our document (applied optimistically)
      }
    } catch (error) {
      console.error('❌ Error handling remote operation:', error, messageData)
      // Don't let errors break the UI
    }
  }

  const applyRemoteChange = (change) => {
    // Fallback for legacy change messages
    if (change && change.content !== undefined && change.content !== null) {
      if (change.changeType === 'INSERT' || change.changeType === 'UPDATE') {
        if (change.content !== lastContentRef.current) {
          setContent(change.content)
          lastSaveRef.current = change.content
          lastContentRef.current = change.content
        }
      }
    }
  }


  const handleContentChange = (e) => {
    try {
      // Ignore changes that are from our own remote operation updates
      if (isUpdatingRef.current) {
        console.log('⚠️ Ignoring content change - update in progress')
        return
      }
      
      const newContent = e.target.value
      const textarea = e.target
      
      // Capture oldContent from lastContentRef BEFORE any updates
      // This MUST be the state before the user's change
      // For delete operations, we MUST have the exact previous state
      const oldContent = lastContentRef.current || content || ''
      
      // Calculate the correct position for the operation
      // For insertions, cursorPosition is AFTER the inserted text
      // For deletions, cursorPosition is at the deletion point
      let cursorPosition = textarea.selectionStart
      const selectionEnd = textarea.selectionEnd
      
      // Check if there was a selection (replacement case)
      const hadSelection = selectionEnd > cursorPosition
      
      // For insertions, the cursor is after the inserted text, so we need to calculate
      // the insertion position correctly
      if (newContent.length > oldContent.length) {
        const insertedLength = newContent.length - oldContent.length
        if (hadSelection) {
          // Replacement case: user selected text and typed (delete + insert)
          // The insertion position is at the start of the selection
          // cursorPosition is already at the insertion point in this case
        } else {
          // Simple insertion: cursor moved forward by insertedLength
          // The insertion position is where the cursor was BEFORE the insertion
          cursorPosition = Math.max(0, cursorPosition - insertedLength)
        }
      }
      // For deletions, cursorPosition is already at the correct position

      // Only process if content actually changed
      if (newContent === oldContent) {
        return
      }
      
      // Verify oldContent matches what we expect
      // If textarea value before change doesn't match lastContentRef, there's a sync issue
      // In that case, we should use the textarea's previous value if we can detect it
      // But since we can't easily get the previous value, we'll trust lastContentRef
      // and log a warning if there's a significant mismatch
      
      // For delete operations, ensure we detect them immediately
      // If content decreased, this is definitely a delete operation
      if (newContent.length < oldContent.length) {
        console.log('🔍 Delete detected:', {
          oldLength: oldContent.length,
          newLength: newContent.length,
          deleted: oldContent.length - newContent.length,
          cursorPosition,
          oldContentPreview: oldContent.substring(Math.max(0, cursorPosition - 10), cursorPosition + 10),
          newContentPreview: newContent.substring(Math.max(0, cursorPosition - 10), cursorPosition + 10),
          oldContentFull: oldContent,
          newContentFull: newContent
        })
      }

      // Create operation BEFORE updating refs to ensure we have correct oldContent
      // Create operation from the change
      // Use temporary operationId for client-side tracking only
      // Server will assign its own operationId when the operation arrives (server is source of truth)
      const tempOperationId = operationIdCounterRef.current++
      const baseVersion = 0
      let operation = OperationalTransform.createOperationFromChange(
        oldContent,
        newContent,
        cursorPosition,
        user.id,
        document.id,
        baseVersion,
        tempOperationId // Temporary ID for client tracking
      )
      
      // Store the temporary ID so we can match server acknowledgments
      operation.tempOperationId = tempOperationId

      // If content decreased but operation is not DELETE, force it to be DELETE
      if (newContent.length < oldContent.length && operation.type !== 'DELETE') {
        console.error('❌ Content decreased but operation is not DELETE! Forcing DELETE:', {
          operationType: operation.type,
          oldLength: oldContent.length,
          newLength: newContent.length,
          operation
        })
        // Force create a DELETE operation using the diff algorithm
        const deletedLength = oldContent.length - newContent.length
        // Use cursor position as hint for where deletion happened
        let deletePosition = cursorPosition
        if (deletePosition > oldContent.length) {
          deletePosition = oldContent.length - deletedLength
        }
        if (deletePosition < 0) {
          deletePosition = 0
        }
        // Try to find the actual deletion point by comparing strings
        let found = false
        for (let i = Math.max(0, deletePosition - deletedLength); i <= Math.min(oldContent.length - deletedLength, deletePosition + deletedLength); i++) {
          const before = oldContent.substring(0, i)
          const after = oldContent.substring(i + deletedLength)
          if (before + after === newContent) {
            deletePosition = i
            found = true
            break
          }
        }
        if (!found) {
          // Fallback: use simple diff
          let start = 0
          while (start < Math.min(oldContent.length, newContent.length) && oldContent.charAt(start) === newContent.charAt(start)) {
            start++
          }
          deletePosition = start
        }
        operation = Operation.delete(deletedLength, deletePosition, user.id, document.id, tempOperationId, baseVersion)
        console.log('✅ Forced DELETE operation:', { length: deletedLength, position: deletePosition })
      }

      // Validate operation before sending
      if (operation.type === 'DELETE') {
        if (operation.length == null || operation.length <= 0) {
          console.error('❌ Invalid DELETE operation created - length is null or <= 0:', {
            operation,
            oldContent,
            newContent,
            cursorPosition
          })
          // Try to fix it - calculate correct length
          const expectedDeleted = oldContent.length - newContent.length
          if (expectedDeleted > 0) {
            operation.length = expectedDeleted
            console.warn('⚠️ Fixed DELETE operation length:', expectedDeleted)
          } else {
            console.error('❌ Cannot fix DELETE operation - skipping')
            return
          }
        }
        if (operation.position == null || operation.position < 0) {
          console.error('❌ Invalid DELETE operation created - position is null or < 0:', {
            operation,
            oldContent,
            newContent,
            cursorPosition
          })
          // Try to fix position
          const deletedLength = operation.length || (oldContent.length - newContent.length)
          let fixedPosition = cursorPosition
          if (fixedPosition > oldContent.length - deletedLength) {
            fixedPosition = oldContent.length - deletedLength
          }
          if (fixedPosition < 0) fixedPosition = 0
          operation.position = fixedPosition
          console.warn('⚠️ Fixed DELETE operation position:', fixedPosition)
        }
      }
      
      console.log('📤 Created operation:', operation.type, 'at position', operation.position, 
                  'content:', operation.content || `length: ${operation.length}`,
                  'baseVersion:', baseVersion, 'tempOperationId:', tempOperationId,
                  'oldContent length:', oldContent.length, 'newContent length:', newContent.length)
      
      // Validate delete operation was created correctly
      if (operation.type === 'DELETE' && newContent.length < oldContent.length) {
        const expectedDeleted = oldContent.length - newContent.length
        if (operation.length !== expectedDeleted) {
          console.warn('⚠️ Delete operation length mismatch:', {
            expected: expectedDeleted,
            actual: operation.length,
            oldContent,
            newContent,
            operation
          })
          // Fix the length
          operation.length = expectedDeleted
        }
      }


      // Add to LOCAL BUFFER: Store operation until server acknowledges it
      // This is part of the OT architecture - operations are stored locally
      // until the server processes and broadcasts them
      pendingOperationsRef.current.push(operation)
      if (pendingOperationsRef.current.length > 100) {
        pendingOperationsRef.current.shift()
      }

      // Update cursor position
      updateCursorPosition(cursorPosition)

      // Send operation to server IMMEDIATELY (no debouncing for real-time sync)
      if (document && document.id && websocketService) {
        // Check if WebSocket is connected before sending
        // Always use current username from user object
        // This ensures username changes are reflected immediately in WebSocket messages
        const userName = user.username || user.name || `User ${user.id}`
        const sent = websocketService.sendOperation(
          document.id, 
          user.id, 
          userName, 
          operation
        )
        if (!sent) {
          console.error('❌ Failed to send operation to server!', {
            documentId: document.id,
            userId: user.id,
            tempOperationId: tempOperationId,
            operationType: operation.type,
            baseVersion: baseVersion,
            documentVersion: documentVersionRef.current,
            oldContentLength: oldContent.length,
            newContentLength: newContent.length
          })
          // If WebSocket isn't connected, queue the operation for retry
          // This ensures operations aren't lost for version 0 documents
          console.warn('⚠️ WebSocket not connected, operation will be retried when connection is established')
        } else {
          console.log('✅ Operation sent to server:', {
            tempOperationId: tempOperationId,
            type: operation.type,
            position: operation.position,
            content: operation.content || `delete ${operation.length} chars`,
            baseVersion: baseVersion,
            documentVersion: documentVersionRef.current
          })
        }
      } else {
        console.error('❌ Cannot send operation - missing document, id, or websocketService', {
          hasDocument: !!document,
          documentId: document?.id,
          hasWebsocketService: !!websocketService
        })
      }
      
      // Update lastContentRef and React state AFTER sending operation
      // This ensures the next operation uses the correct baseline content
      // Without this, DELETE operations and subsequent operations will use wrong baseline
      lastContentRef.current = newContent
      setContent(newContent)
      console.log('✅ Updated lastContentRef and state after sending operation:', {
        newContentLength: newContent.length,
        operationType: operation.type
      })

      // Clear existing timeout
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current)
      }

      // Save document more frequently to ensure content is persisted
      // This is important for version 0 documents where content might be lost on exit
      // Debounced save (only for persistence, not for real-time sync)
      // Reduced timeout to 1 second to ensure content is saved more quickly
      saveTimeoutRef.current = setTimeout(() => {
        saveDocument()
      }, 1000)

      // Note: Version creation is now only done when user exits editing screen
      // No automatic version creation on content change
    } catch (error) {
      console.error('Error handling content change:', error)
      // Don't break the UI on error
    }
  }

  const createVersionOnExit = async () => {
    if (!document || !document.id) return
    
    // Save document content first to ensure it's persisted
    // Use textarea value directly, not React state
    const currentContent = textareaRef.current?.value || lastContentRef.current || ''
    
    // First, ensure document content is saved to database
    if (currentContent !== lastSaveRef.current) {
      try {
        console.log('Saving document content before exit...')
        await documentService.updateDocument(
          document.id,
          user.id,
          currentContent
        )
        lastSaveRef.current = currentContent
        console.log('Document content saved successfully')
      } catch (err) {
        console.error('Failed to save document content on exit:', err)
        // Continue anyway to try creating version
      }
    }
    
    const lastVersionedContent = lastVersionedContentRef.current || ''
    
    // Normalize content for comparison
    const normalizedCurrent = currentContent.trim()
    const normalizedLastVersioned = lastVersionedContent.trim()
    
    // Only create version if content actually changed
    if (normalizedCurrent !== normalizedLastVersioned && normalizedCurrent !== '') {
      try {
        await versionService.createVersion(
          document.id,
          currentContent,
          user.id,
          'Document edited (exited editing screen)'
        )
        // Update last versioned content
        lastVersionedContentRef.current = currentContent
        console.log('Version created on exit successfully')
      } catch (err) {
        // Silently handle "no changes" error, but log other errors
        if (err.response?.data?.error && err.response.data.error.includes('No changes detected')) {
          // This is expected if user hasn't made changes, don't show error
          return
        }
        console.warn('Failed to create version on exit:', err)
      }
    }
  }

  const saveDocument = async () => {
    if (!document || saving) return
    
    // Use textarea value directly, not React state
    const currentContent = textareaRef.current?.value || lastContentRef.current || ''
    if (currentContent === lastSaveRef.current) return
    
    try {
      setSaving(true)
      const updated = await documentService.updateDocument(
        document.id,
        user.id,
        currentContent
      )
      setDocument(updated)
      lastSaveRef.current = currentContent
      
      // Create a version snapshot (only on manual save, not auto-save)
      // Uncomment if you want versions on every save
      // await versionService.createVersion(
      //   document.id,
      //   content,
      //   user.id,
      //   'Auto-saved version'
      // )
    } catch (err) {
      console.error('Failed to save:', err)
      setError(err.response?.data?.error || 'Failed to save document')
    } finally {
      setSaving(false)
    }
  }

  const handleManualSave = async () => {
    // First save the document
    await saveDocument()
    
    // FIX: Use textarea value directly, not React state (which may be stale)
    const currentContent = textareaRef.current?.value || lastContentRef.current || content || ''
    const lastVersionedContent = lastVersionedContentRef.current || ''
    
    // Normalize content for comparison
    const normalizedCurrent = currentContent.trim()
    const normalizedLastVersioned = lastVersionedContent.trim()
    
    // Only create version if content actually changed
    if (normalizedCurrent === normalizedLastVersioned) {
      setError('No changes detected. The document content is identical to the latest version.')
      setTimeout(() => setError(''), 3000)
      return
    }
    
    // Create version on manual save
    try {
      const newVersion = await versionService.createVersion(
        document.id,
        currentContent,
        user.id,
        'Manual save'
      )
      
      // Update last versioned content and creator
      lastVersionedContentRef.current = currentContent
      lastVersionCreatedByRef.current = user.id
      
      // Refresh version history if it's currently displayed
      if (showVersionHistory) {
        const history = await versionService.getVersionHistory(document.id)
        setVersionHistory(history)
      }
      
      // Show success message
      const successMsg = `Version ${newVersion.versionNumber} created successfully!`
      setError('')
      setSuccess(successMsg)
      setTimeout(() => setSuccess(''), 3000)
    } catch (err) {
      const errorMsg = err.response?.data?.error || err.message || 'Failed to create version'
      setError(errorMsg)
      setTimeout(() => setError(''), 5000)
    }
  }

  const fetchCollaboratorUsernames = async (collaboratorIds) => {
    try {
      const users = await authService.getUsersByIds(collaboratorIds)
      const usernameMap = {}
      users.forEach(user => {
        if (user && user.id) {
          usernameMap[user.id] = user.username || `User ${user.id}`
        }
      })
      setCollaboratorUsernames(prev => ({ ...prev, ...usernameMap }))
    } catch (err) {
      console.error('Failed to fetch collaborator usernames:', err)
      // Set fallback usernames
      const fallbackMap = {}
      collaboratorIds.forEach(id => {
        fallbackMap[id] = `User ${id}`
      })
      setCollaboratorUsernames(prev => ({ ...prev, ...fallbackMap }))
    }
  }

  const handleAddCollaborator = async () => {
    if (!collaboratorId) {
      setError('Please enter a collaborator ID')
      return
    }
    const collaboratorIdNum = parseInt(collaboratorId)
    // Prevent users from adding themselves as collaborators
    if (collaboratorIdNum === user.id) {
      setError('You cannot add yourself as a collaborator')
      return
    }
    try {
      const updated = await documentService.addCollaborator(
        document.id,
        document.ownerId,
        collaboratorIdNum
      )
      setDocument(updated)
      setCollaboratorId('')
      setError('')
      // Fetch username for the newly added collaborator
      if (updated.collaboratorIds && updated.collaboratorIds.includes(collaboratorIdNum)) {
        fetchCollaboratorUsernames([collaboratorIdNum])
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to add collaborator')
    }
  }

  const loadVersionHistory = async () => {
    try {
      // Use new endpoint that includes diffs and usernames
      const history = await versionService.getVersionHistoryWithDiffs(document.id)
      
      setVersionHistory(history)
      setShowVersionHistory(true)
      
      // Update last versioned content and creator if we have versions
      if (history && history.length > 0) {
        const latestVersion = history[0]
        lastVersionedContentRef.current = latestVersion.content || content
        lastVersionCreatedByRef.current = latestVersion.createdBy
      }
    } catch (err) {
      console.error('Failed to load version history:', err)
      setError('Failed to load version history')
    }
  }

  const handleBackButton = async () => {
    if (!document || !document.id) {
      navigate('/dashboard')
      return
    }

    const currentContent = textareaRef.current?.value || lastContentRef.current || content || ''
    
    // Fetch latest version history to check if another user has saved
    let shouldPromptToSave = false
    let promptMessage = ''
    
    try {
      const history = await versionService.getVersionHistory(document.id)
      let latestVersion = null
      if (history && history.length > 0) {
        latestVersion = history[0]
      }
      
      // Check if another user has saved a version since we last checked
      const anotherUserSaved = latestVersion && 
                               latestVersion.createdBy && 
                               String(latestVersion.createdBy) !== String(user.id) &&
                               (lastVersionCreatedByRef.current === null || 
                                String(latestVersion.createdBy) !== String(lastVersionCreatedByRef.current))
      
      // Check if current user has unversioned changes
      const lastVersionedContent = latestVersion ? (latestVersion.content || '') : (lastVersionedContentRef.current || '')
      const normalizedCurrent = currentContent.trim()
      const normalizedLastVersioned = lastVersionedContent.trim()
      const hasUnversionedChanges = normalizedCurrent !== normalizedLastVersioned && normalizedCurrent !== ''
      
      // Always prompt if another user has saved, even if current user hasn't made changes
      if (anotherUserSaved) {
        shouldPromptToSave = true
        promptMessage = 'Another user has saved a version of this document. Would you like to save your current version before exiting?'
      } else if (hasUnversionedChanges) {
        shouldPromptToSave = true
        promptMessage = 'You have unsaved changes. Would you like to save the current version before exiting?'
      }
      
      if (shouldPromptToSave) {
        const shouldSave = window.confirm(promptMessage)
        
        if (shouldSave) {
          try {
            await versionService.createVersion(
              document.id,
              currentContent,
              user.id,
              'Document edited (exited editing screen)'
            )
            // Update last versioned content and creator
            lastVersionedContentRef.current = currentContent
            lastVersionCreatedByRef.current = user.id
            console.log('Version created before leaving document')
          } catch (err) {
            // Log error but don't prevent navigation
            console.warn('Failed to create version before leaving:', err)
            
            // Better error handling for "no changes detected" case
            const errorMessage = err.response?.data?.error || err.message || 'Failed to save version'
            if (errorMessage.includes('No changes detected') || errorMessage.includes('identical to the latest version')) {
              // User hasn't made changes - this is expected, show friendly message
              alert('No changes to save. The document content is identical to the latest version.')
            } else {
              // Other error - show generic message
              alert('Failed to save version. You can still exit without saving.')
            }
          }
        }
      }
    } catch (err) {
      console.error('Error checking version history before exit:', err)
      // If we can't check, still prompt if there are unversioned changes
      const lastVersionedContent = lastVersionedContentRef.current || ''
      const normalizedCurrent = currentContent.trim()
      const normalizedLastVersioned = lastVersionedContent.trim()
      const hasUnversionedChanges = normalizedCurrent !== normalizedLastVersioned && normalizedCurrent !== ''
      
      if (hasUnversionedChanges) {
        const shouldSave = window.confirm(
          'You have unsaved changes. Would you like to save the current version before exiting?'
        )
        
        if (shouldSave) {
          try {
            await versionService.createVersion(
              document.id,
              currentContent,
              user.id,
              'Document edited (exited editing screen)'
            )
            lastVersionedContentRef.current = currentContent
            lastVersionCreatedByRef.current = user.id
            console.log('Version created before leaving document')
          } catch (err) {
            console.warn('Failed to create version before leaving:', err)
            alert('Failed to save version. You can still exit without saving.')
          }
        }
      }
    }
    
    // Navigate back to dashboard
    navigate('/dashboard')
  }

  // Update cursor position and send via WebSocket
  const updateCursorPosition = (position) => {
    if (!document || !document.id || !websocketService) return
    
    // Debounce cursor updates (send every 100ms max)
    if (cursorUpdateTimeoutRef.current) {
      clearTimeout(cursorUpdateTimeoutRef.current)
    }
    
    cursorUpdateTimeoutRef.current = setTimeout(() => {
      websocketService.sendCursorUpdate(
        document.id,
        user.id,
        position,
        user.username || user.name || `User ${user.id}`
      )
    }, 100)
  }

  // Handle cursor position changes (selection, mouse, keyboard)
  const handleCursorChange = () => {
    const textarea = textareaRef.current
    if (!textarea) return
    const position = textarea.selectionStart
    updateCursorPosition(position)
  }


  const handleExport = async (format) => {
    try {
      setError('')
      setSuccess('')
      await documentService.exportDocument(document.id, format, user.id)
      setSuccess(`Document exported as ${format.toUpperCase()} successfully!`)
      setTimeout(() => setSuccess(''), 3000)
      setShowExportMenu(false)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to export document')
      setTimeout(() => setError(''), 5000)
      setShowExportMenu(false)
    }
  }

  // Close export menu when clicking outside
  useEffect(() => {
    if (typeof window === 'undefined' || !window.document) return
    
    const handleClickOutside = (event) => {
      if (showExportMenu && !event.target.closest('.export-menu') && 
          !event.target.closest('button[title="Export document"]')) {
        setShowExportMenu(false)
      }
    }
    window.document.addEventListener('mousedown', handleClickOutside)
    return () => {
      if (typeof window !== 'undefined' && window.document) {
        window.document.removeEventListener('mousedown', handleClickOutside)
      }
    }
  }, [showExportMenu])

  const handleRevertVersion = async (versionNumber) => {
    // Confirmation
    if (!window.confirm(`Are you sure you want to restore the document content from version ${versionNumber}? A new version will be created with the restored content, and all existing versions will be preserved.`)) {
      return
    }
    
    try {
      setError('')
      setSuccess('')
      const reverted = await versionService.revertToVersion(
        document.id,
        versionNumber,
        user.id
      )
      
      // Allow empty strings (version 0 might have empty content)
      // Check if we have a valid response with versionNumber (the key field)
      if (!reverted || reverted.versionNumber === undefined) {
        throw new Error('Invalid response from server')
      }
      // Use empty string if content is null/undefined, otherwise use the actual content (even if empty string)
      const revertedContent = (reverted.content !== undefined && reverted.content !== null) ? reverted.content : ''
      
      // Update all content references
      setContent(revertedContent)
      setDocument({ ...document, content: revertedContent })
      lastSaveRef.current = revertedContent
      lastContentRef.current = revertedContent
      lastVersionedContentRef.current = revertedContent
      initialContentRef.current = revertedContent // Update initial content ref for back button check
      
      // Update textarea directly
      if (textareaRef.current) {
        const cursorPosition = textareaRef.current.selectionStart || 0
        const scrollTop = textareaRef.current.scrollTop || 0
        const scrollLeft = textareaRef.current.scrollLeft || 0
        textareaRef.current.value = revertedContent
        textareaRef.current.setSelectionRange(cursorPosition, cursorPosition)
        textareaRef.current.scrollTop = scrollTop
        textareaRef.current.scrollLeft = scrollLeft
      }
      
      // Reset document version ref and reconnect WebSocket to enable real-time editing
      // Always use version 0 as base for real-time operations
      documentVersionRef.current = 0
      
      // Clear pending operations as they may be based on old content
      pendingOperationsRef.current = []
      
      // Disconnect and reconnect WebSocket to reset state
      websocketService.disconnect()
      setTimeout(() => {
        if (document && document.id) {
          connectWebSocket()
          console.log('✅ WebSocket reconnected after revert - real-time editing enabled')
        }
      }, 500)
      
      // Refresh version history to show the new revert version
      const history = await versionService.getVersionHistory(document.id)
      setVersionHistory(history)
      // Update last versioned content and creator to reflect the new revert version
      if (reverted && reverted.versionNumber !== undefined) {
        lastVersionedContentRef.current = revertedContent
        lastVersionCreatedByRef.current = user.id
      }
      setShowVersionHistory(false)
      setError('')
      setSuccess(`Successfully restored content from version ${versionNumber}. A new version has been created and all previous versions are preserved. Real-time editing is now enabled.`)
      setTimeout(() => setSuccess(''), 5000)
    } catch (err) {
      console.error('Error reverting version:', err)
      const errorMessage = err.response?.data?.error || err.message || 'Failed to restore version'
      setError(`Failed to restore version: ${errorMessage}`)
      setTimeout(() => setError(''), 5000)
    }
  }

  if (loading) {
    return <div className="loading">Loading document...</div>
  }

  if (error && !document) {
    return (
      <div className="container">
        <div className="error">{error}</div>
        <button onClick={() => navigate('/dashboard')} className="btn btn-primary">
          Back to Dashboard
        </button>
      </div>
    )
  }

  const canEdit = document && (document.ownerId === user.id || 
    (document.collaboratorIds && document.collaboratorIds.includes(user.id)))


  return (
    <div className="document-editor">
      <header className="editor-header">
        <div className="header-content">
          <button onClick={handleBackButton} className="btn btn-secondary">
            ← Back
          </button>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="title-input"
            placeholder="Document Title"
            disabled={!canEdit}
          />
            <div className="editor-actions" style={{ position: 'relative' }}>
            {saving && <span className="saving-indicator">Saving...</span>}
            {/* Active Users Indicator */}
            {activeUsers.length > 0 && (
              <div className="active-users-indicator" title={`${activeUsers.length} user(s) currently editing`}>
                <span className="active-users-count">{activeUsers.length}</span>
                <span className="active-users-icon">👥</span>
              </div>
            )}
            {canEdit && (
              <>
                <button onClick={handleManualSave} className="btn btn-primary">
                  Save Version
                </button>
                <div style={{ position: 'relative', display: 'inline-block' }}>
                  <button 
                    onClick={() => setShowExportMenu(!showExportMenu)} 
                    className="btn btn-secondary"
                    title="Export document"
                  >
                    Export
                  </button>
                  {showExportMenu && (
                    <div className="export-menu" style={{
                      position: 'absolute',
                      top: '100%',
                      right: 0,
                      marginTop: '5px',
                      backgroundColor: 'white',
                      border: '1px solid #ddd',
                      borderRadius: '5px',
                      boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                      zIndex: 1000,
                      padding: '10px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '5px'
                    }}>
                      <button 
                        onClick={() => handleExport('docx')} 
                        className="btn btn-sm btn-secondary"
                      >
                        Export as Word (.docx)
                      </button>
                      <button 
                        onClick={() => handleExport('txt')} 
                        className="btn btn-sm btn-secondary"
                      >
                        Export as Text (.txt)
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}
            <button onClick={loadVersionHistory} className="btn btn-secondary">
              Version History
            </button>
            {document.ownerId === user.id && (
              <button onClick={() => setShowCollaborators(!showCollaborators)} className="btn btn-secondary">
                Collaborators
              </button>
            )}
          </div>
        </div>
      </header>

      <div className="editor-container">
        {error && <div className="error">{error}</div>}
        {success && <div className="success" style={{ backgroundColor: '#4CAF50', color: 'white', padding: '10px', margin: '10px 0', borderRadius: '4px' }}>{success}</div>}
        
        <div className="editor-main">
          <div className="editor-wrapper">
            <div className="editor-textarea-wrapper" style={{ position: 'relative' }}>
              {!loading && document ? (
                <textarea
                  ref={textareaRef}
                  key={`doc-${document.id}-loaded`}
                  defaultValue={content || ''}
                  onChange={handleContentChange}
                  onSelect={handleCursorChange}
                  onKeyUp={handleCursorChange}
                  onMouseUp={handleCursorChange}
                  onClick={handleCursorChange}
                  className="document-content"
                  placeholder="Start typing your document..."
                  disabled={!canEdit}
                />
              ) : (
                <textarea
                  ref={textareaRef}
                  key="doc-loading"
                  defaultValue=""
                  className="document-content"
                  placeholder="Loading document..."
                  disabled={true}
                />
              )}
              {/* Render remote cursors */}
              {Object.values(remoteCursors).map(cursor => {
                if (cursor.position === null || cursor.position === undefined) return null
                return (
                  <RemoteCursor
                    key={cursor.userId}
                    cursor={cursor}
                    textarea={textareaRef.current}
                    content={textareaRef.current?.value || content}
                  />
                )
              })}
            </div>
          </div>
          {!canEdit && (
            <div className="read-only-notice">
              You have read-only access to this document. Contact the owner to get edit access.
            </div>
          )}
        </div>

        {showVersionHistory && (
          <div className="sidebar">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
              <h3>Version History</h3>
              <button
                onClick={() => setShowVersionHistory(false)}
                className="btn btn-secondary btn-sm"
              >
                Close
              </button>
            </div>
            <div className="version-list">
              {versionHistory.length === 0 ? (
                <p>No versions yet. Versions are automatically created when you edit the document.</p>
              ) : (
                versionHistory.map((version, index) => (
                  <div 
                    key={version.id} 
                    className="version-item"
                    style={{
                      border: index === 0 ? '2px solid #4CAF50' : '1px solid #ddd',
                      padding: '12px',
                      marginBottom: '10px',
                      borderRadius: '4px',
                      backgroundColor: index === 0 ? '#f0f8f0' : 'white'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <p style={{ margin: 0, fontWeight: 'bold', fontSize: '16px' }}>
                        Version {version.versionNumber}
                        {index === 0 && <span style={{ marginLeft: '8px', fontSize: '12px', color: '#4CAF50' }}>(Latest)</span>}
                      </p>
                    </div>
                    <p className="version-meta" style={{ margin: '4px 0', fontSize: '12px', color: '#666' }}>
                      {new Date(version.createdAt).toLocaleString()}
                    </p>
                    {version.changeDescription && (
                      <p className="version-description" style={{ margin: '4px 0', fontSize: '13px', color: '#555' }}>
                        {version.changeDescription}
                      </p>
                    )}
                    <p style={{ margin: '4px 0', fontSize: '11px', color: '#888' }}>
                      Created by {version.createdByUsername || `User ${version.createdBy}`}
                    </p>
                    {/* Display GitHub-style diff stats */}
                    {version.diffStats && (
                      <div style={{ marginTop: '10px', padding: '8px', backgroundColor: '#f9f9f9', borderRadius: '4px' }}>
                        <div style={{ display: 'flex', gap: '15px', fontSize: '12px', marginBottom: '8px' }}>
                          {version.diffStats.addedLines > 0 && (
                            <span style={{ color: '#28a745', fontWeight: '600' }}>
                              +{version.diffStats.addedLines} lines added
                            </span>
                          )}
                          {version.diffStats.removedLines > 0 && (
                            <span style={{ color: '#d73a49', fontWeight: '600' }}>
                              -{version.diffStats.removedLines} lines removed
                            </span>
                          )}
                          {version.diffStats.addedLines === 0 && version.diffStats.removedLines === 0 && (
                            <span style={{ color: '#586069', fontStyle: 'italic' }}>
                              No changes
                            </span>
                          )}
                        </div>
                        {version.diffStats.netChange !== 0 && (
                          <div style={{ fontSize: '11px', color: '#586069' }}>
                            Net change: {version.diffStats.netChange > 0 ? '+' : ''}{version.diffStats.netChange} characters
                          </div>
                        )}
                        {/* Link to view full diff - available for all versions with changes */}
                        {version.diffStats && 
                         (version.diffStats.addedLines > 0 || version.diffStats.removedLines > 0) && (
                          <button
                            onClick={() => navigate(`/document/${document.id}/diff/${version.versionNumber}`)}
                            className="btn btn-sm btn-secondary"
                            style={{ marginTop: '8px', fontSize: '11px', padding: '4px 8px' }}
                          >
                            View Full Diff
                          </button>
                        )}
                      </div>
                    )}
                    {canEdit && (
                      <button
                        onClick={() => handleRevertVersion(version.versionNumber)}
                        className="btn btn-sm btn-primary"
                        style={{ marginTop: '8px' }}
                      >
                        Revert to this version
                      </button>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {showCollaborators && (
          <div className="sidebar">
            <h3>Collaborators</h3>
            <button
              onClick={() => setShowCollaborators(false)}
              className="btn btn-secondary btn-sm"
            >
              Close
            </button>
            {document.collaboratorIds && document.collaboratorIds.length > 0 ? (
              <div className="collaborator-list">
                <p>Current collaborators: {document.collaboratorIds.length}</p>
                <div style={{ marginTop: '10px' }}>
                  {document.collaboratorIds.map(collabId => (
                    <div key={collabId} style={{ 
                      padding: '8px', 
                      margin: '5px 0', 
                      backgroundColor: '#f5f5f5', 
                      borderRadius: '4px',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center'
                    }}>
                      <span>{collaboratorUsernames[collabId] || `User ${collabId}`}</span>
                      <button
                        onClick={async () => {
                          try {
                            const updated = await documentService.removeCollaborator(
                              document.id,
                              document.ownerId,
                              collabId
                            )
                            setDocument(updated)
                            // Remove from username map
                            setCollaboratorUsernames(prev => {
                              const newMap = { ...prev }
                              delete newMap[collabId]
                              return newMap
                            })
                          } catch (err) {
                            setError(err.response?.data?.error || 'Failed to remove collaborator')
                          }
                        }}
                        className="btn btn-danger btn-sm"
                        style={{ padding: '2px 8px', fontSize: '12px' }}
                      >
                        Remove
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <p>No collaborators yet</p>
            )}
            <div className="add-collaborator">
              <input
                type="number"
                placeholder="User ID to add as collaborator"
                value={collaboratorId}
                onChange={(e) => setCollaboratorId(e.target.value)}
                className="input"
              />
              <button onClick={handleAddCollaborator} className="btn btn-primary btn-sm">
                Add Collaborator
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default DocumentEditor
