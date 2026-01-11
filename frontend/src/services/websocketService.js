import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

let stompClient = null
let subscriptions = {}
let operationSubscriptions = {}
let cursorSubscriptions = {}
let userSubscriptions = {}
let currentDocumentId = null
let currentUserId = null
let currentUserName = null

export const websocketService = {
  connect(documentId, userId, userName, onMessage, onOperation, onCursor, onUserEvent) {
    // Disconnect existing connection if any
    if (stompClient) {
      this.disconnect()
    }

    // Store current connection info
    currentDocumentId = documentId
    currentUserId = userId
    currentUserName = userName

    // Connect directly to document-editing-service to bypass API Gateway WebSocket issues
    // API Gateway has limited WebSocket support, especially for SockJS
    // In production, you might want to use a proper WebSocket gateway or nginx
    const wsUrl = process.env.NODE_ENV === 'production' 
      ? '/ws'  // Use proxy in production (nginx handles it)
      : 'http://localhost:8084/ws'  // Direct connection in development
    // Configure SockJS to not send credentials to avoid CORS issues with wildcard origins
    const socket = new SockJS(wsUrl, null, {
      transports: ['websocket', 'xhr-streaming', 'xhr-polling']
    })
    stompClient = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectHeaders: {
        userId: userId.toString(),
        userName: userName || `User ${userId}`
      },
      onConnect: () => {
        console.log('✅ WebSocket connected successfully for document:', documentId, 'user:', userId)
        // Subscribe immediately when connected
        // This ensures operations can be sent/received even for version 0 empty documents
        this.subscribe(documentId, userId, userName, onMessage, onOperation, onCursor, onUserEvent)
        console.log('✅ WebSocket subscription initiated for document:', documentId)
      },
      onWebSocketError: (error) => {
        console.error('WebSocket error:', error)
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame)
      },
      onWebSocketClose: () => {
        console.log('WebSocket closed')
      },
      onDisconnect: () => {
        console.log('STOMP disconnected')
      }
    })

    stompClient.activate()
  },

  subscribe(documentId, userId, userName, onMessage, onOperation, onCursor, onUserEvent) {
    if (!stompClient) {
      console.warn('STOMP client is null')
      return
    }
    if (!stompClient.connected) {
      console.warn('STOMP client not connected, waiting...')
      // Wait a bit and retry
      setTimeout(() => {
        if (stompClient && stompClient.connected) {
          this.subscribe(documentId, userId, userName, onMessage, onOperation, onCursor, onUserEvent)
        }
      }, 1000)
      return
    }
    console.log('Subscribing to document room:', documentId, 'userId:', userId)

    // Subscribe to legacy change messages
    const subscription = stompClient.subscribe(
      `/topic/document/${documentId}`,
      (message) => {
        const change = JSON.parse(message.body)
        if (onMessage) onMessage(change)
      },
      {
        userId: userId.toString(),
        userName: userName || `User ${userId}`
      }
    )
    subscriptions[documentId] = subscription

    // Subscribe to OT operations
    if (onOperation) {
      const opSubscription = stompClient.subscribe(
        `/topic/document/${documentId}/operations`,
        (message) => {
          try {
            const data = JSON.parse(message.body)
            console.log('🔔 WebSocket received operation on /topic/document/' + documentId + '/operations:', data)
            if (onOperation) {
              onOperation(data)
            } else {
              console.warn('⚠️ onOperation callback is not defined!')
            }
          } catch (error) {
            console.error('❌ Error parsing operation message:', error, message.body)
          }
        },
        {
          userId: userId.toString(),
          userName: userName || `User ${userId}`
        }
      )
      operationSubscriptions[documentId] = opSubscription
      console.log('✅ Subscribed to operations channel for document:', documentId, 'on topic: /topic/document/' + documentId + '/operations')
    } else {
      console.warn('⚠️ onOperation callback not provided - operations will not be processed!')
    }

    // Subscribe to cursor updates
    if (onCursor) {
      const cursorSubscription = stompClient.subscribe(
        `/topic/document/${documentId}/cursors`,
        (message) => {
          const cursor = JSON.parse(message.body)
          if (onCursor) onCursor(cursor)
        },
        {
          userId: userId.toString(),
          userName: userName || `User ${userId}`
        }
      )
      cursorSubscriptions[documentId] = cursorSubscription
    }

    // Subscribe to user join/leave notifications
    if (onUserEvent) {
      const userSubscription = stompClient.subscribe(
        `/topic/document/${documentId}/users`,
        (message) => {
          try {
            const data = JSON.parse(message.body)
            console.log('WebSocket received user event:', data)
            if (onUserEvent) onUserEvent(data)
          } catch (error) {
            console.error('Error parsing user event message:', error, message.body)
          }
        },
        {
          userId: userId.toString(),
          userName: userName || `User ${userId}`
        }
      )
      userSubscriptions[documentId] = userSubscription
      console.log('Subscribed to user events channel for document:', documentId)
      
      // Request initial users list by subscribing to the subscribe mapping endpoint
      // This will trigger the @SubscribeMapping which returns the current users list
      setTimeout(() => {
        try {
          stompClient.subscribe(
            `/topic/document/${documentId}/users`,
            (message) => {
              // This subscription will receive the initial users list
              try {
                const data = JSON.parse(message.body)
                if (data.type === 'users_list' && onUserEvent) {
                  onUserEvent(data)
                }
              } catch (error) {
                console.error('Error parsing initial users list:', error)
              }
            },
            {
              userId: userId.toString(),
              userName: userName || `User ${userId}`
            }
          )
        } catch (error) {
          console.error('Error requesting initial users list:', error)
        }
      }, 500)
    }
  },

  sendEdit(documentId, userId, changeType, content, position) {
    if (!stompClient || !stompClient.connected) {
      console.warn('STOMP client not connected')
      return
    }

    stompClient.publish({
      destination: '/app/document/edit',
      body: JSON.stringify({
        documentId,
        userId,
        changeType,
        content,
        position
      })
    })
  },

  sendOperation(documentId, userId, userName, operation) {
    if (!stompClient) {
      console.error('❌ STOMP client is null, cannot send operation')
      return false
    }
    if (!stompClient.connected) {
      console.warn('⚠️ STOMP client not connected yet, will retry. Connection state:', stompClient.connected)
      // Retry sending operation after a short delay if WebSocket is not connected
      // This is important for version 0 documents where operations might be sent before connection is established
      setTimeout(() => {
        if (stompClient && stompClient.connected) {
          console.log('🔄 Retrying operation send after WebSocket connection established')
          this.sendOperation(documentId, userId, userName, operation)
        } else {
          console.error('❌ WebSocket still not connected after retry, operation lost')
        }
      }, 500)
      return false
    }

    // Ensure DELETE operations always have valid length and position
    if (operation.type === 'DELETE') {
      if (operation.length == null || operation.length <= 0) {
        console.error('❌ Cannot send DELETE operation - length is invalid:', operation)
        return false
      }
      if (operation.position == null || operation.position < 0) {
        console.error('❌ Cannot send DELETE operation - position is invalid:', operation)
        return false
      }
    }
    
    const message = {
      documentId,
      userId,
      userName: userName || `User ${userId}`,
      operation: {
        type: operation.type,
        content: operation.content || null, // Explicitly set to null for DELETE operations
        length: operation.length != null ? operation.length : null, // Ensure it's a number or null
        position: operation.position != null ? operation.position : null, // Ensure it's a number or null
        userId: operation.userId || userId, // Include userId in operation
        documentId: operation.documentId || documentId, // Include documentId in operation
        // Don't send operationId - server will assign one based on arrival time
        // This ensures server is the source of truth for operation ordering
        baseVersion: operation.baseVersion != null ? operation.baseVersion : 0
      }
    }
    
    console.log('Sending operation to server:', {
      type: operation.type,
      position: operation.position,
      content: operation.content || `delete ${operation.length} chars`,
      tempOperationId: operation.tempOperationId || operation.operationId || 'none'
    })
    
    try {
      stompClient.publish({
        destination: '/app/document/edit',
        body: JSON.stringify(message)
      })
      return true
    } catch (error) {
      console.error('Error sending operation:', error)
      return false
    }
  },

  sendCursorUpdate(documentId, userId, position, userName) {
    if (!stompClient || !stompClient.connected) {
      console.warn('STOMP client not connected')
      return
    }

    stompClient.publish({
      destination: '/app/document/cursor',
      body: JSON.stringify({
        documentId,
        userId,
        position,
        userName
      })
    })
  },

  unsubscribe(documentId) {
    if (subscriptions[documentId]) {
      subscriptions[documentId].unsubscribe()
      delete subscriptions[documentId]
    }
    if (operationSubscriptions[documentId]) {
      operationSubscriptions[documentId].unsubscribe()
      delete operationSubscriptions[documentId]
    }
    if (cursorSubscriptions[documentId]) {
      cursorSubscriptions[documentId].unsubscribe()
      delete cursorSubscriptions[documentId]
    }
    if (userSubscriptions[documentId]) {
      userSubscriptions[documentId].unsubscribe()
      delete userSubscriptions[documentId]
    }
  },

  disconnect() {
    Object.keys(subscriptions).forEach(docId => {
      this.unsubscribe(docId)
    })
    
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
  }
}

