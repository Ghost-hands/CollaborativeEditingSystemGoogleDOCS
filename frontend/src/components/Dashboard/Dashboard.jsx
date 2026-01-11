import React, { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { documentService } from '../../services/documentService'
import { authService } from '../../services/authService'
import './Dashboard.css'

const Dashboard = ({ user, onLogout }) => {
  const [documents, setDocuments] = useState([])
  const [filteredDocuments, setFilteredDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [isSearching, setIsSearching] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newDocument, setNewDocument] = useState({ title: '', content: '' })
  const [collaboratorUsernames, setCollaboratorUsernames] = useState({}) // Map of userId -> username
  const [ownerUsernames, setOwnerUsernames] = useState({}) // Map of userId -> username for document owners
  const navigate = useNavigate()
  const searchTimeoutRef = useRef(null)

  useEffect(() => {
    loadDocuments()
  }, [])

  useEffect(() => {
    // Update filtered documents when documents change (if not searching)
    if (searchQuery.trim() === '') {
      setFilteredDocuments(documents)
    }
  }, [documents, searchQuery])

  useEffect(() => {
    // Debounce search
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current)
    }

    if (searchQuery.trim() === '') {
      setIsSearching(false)
      return
    }

    setIsSearching(true)
    searchTimeoutRef.current = setTimeout(() => {
      performSearch(searchQuery)
    }, 300) // 300ms debounce

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchQuery])

  const fetchCollaboratorUsernames = async (documents) => {
    // Collect all unique collaborator IDs and owner IDs from all documents
    const allCollaboratorIds = new Set()
    const allOwnerIds = new Set()
    documents.forEach(doc => {
      if (doc.collaboratorIds && doc.collaboratorIds.length > 0) {
        doc.collaboratorIds.forEach(id => allCollaboratorIds.add(id))
      }
      if (doc.ownerId) {
        allOwnerIds.add(doc.ownerId)
      }
    })
    
    // Fetch collaborator usernames
    if (allCollaboratorIds.size > 0) {
      try {
        const userIds = Array.from(allCollaboratorIds)
        const users = await authService.getUsersByIds(userIds)
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
        allCollaboratorIds.forEach(id => {
          fallbackMap[id] = `User ${id}`
        })
        setCollaboratorUsernames(prev => ({ ...prev, ...fallbackMap }))
      }
    }
    
    // Fetch owner usernames
    if (allOwnerIds.size > 0) {
      try {
        const userIds = Array.from(allOwnerIds)
        const users = await authService.getUsersByIds(userIds)
        const usernameMap = {}
        users.forEach(user => {
          if (user && user.id) {
            usernameMap[user.id] = user.username || `User ${user.id}`
          }
        })
        setOwnerUsernames(prev => ({ ...prev, ...usernameMap }))
      } catch (err) {
        console.error('Failed to fetch owner usernames:', err)
        // Set fallback usernames
        const fallbackMap = {}
        allOwnerIds.forEach(id => {
          fallbackMap[id] = `User ${id}`
        })
        setOwnerUsernames(prev => ({ ...prev, ...fallbackMap }))
      }
    }
  }

  const loadDocuments = async () => {
    try {
      setLoading(true)
      // Only load documents accessible by the current user (owner or collaborator)
      const docs = await documentService.getAllDocuments(user.id)
      setDocuments(docs)
      setFilteredDocuments(docs)
      // Fetch usernames for all collaborators
      await fetchCollaboratorUsernames(docs)
    } catch (err) {
      setError('Failed to load documents')
      console.error('Error loading documents:', err)
    } finally {
      setLoading(false)
    }
  }

  const performSearch = async (query) => {
    if (!query || query.trim() === '') {
      setFilteredDocuments(documents)
      setIsSearching(false)
      return
    }

    try {
      const results = await documentService.searchDocuments(user.id, query)
      setFilteredDocuments(results)
    } catch (err) {
      console.error('Error performing search:', err)
      // Fallback to client-side filtering
      const filtered = documents.filter(doc => {
        const title = (doc.title || '').toLowerCase()
        const content = (doc.content || '').toLowerCase()
        const searchLower = query.toLowerCase()
        return title.includes(searchLower) || content.includes(searchLower)
      })
      setFilteredDocuments(filtered)
    } finally {
      setIsSearching(false)
    }
  }

  const handleCreateDocument = async (e) => {
    e.preventDefault()
    try {
      const doc = await documentService.createDocument({
        ...newDocument,
        ownerId: user.id
      })
      setDocuments([...documents, doc])
      setFilteredDocuments([...filteredDocuments, doc])
      setShowCreateModal(false)
      setNewDocument({ title: '', content: '' })
      navigate(`/document/${doc.id}`)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create document')
    }
  }

  const handleDeleteDocument = async (id, doc) => {
    const isOwner = doc.ownerId === user.id
    const isCollaborator = doc.collaboratorIds && doc.collaboratorIds.includes(user.id)
    
    let confirmMessage = ''
    if (isOwner) {
      confirmMessage = 'Are you sure you want to delete this document? This action cannot be undone.'
    } else if (isCollaborator) {
      confirmMessage = 'Are you sure you want to leave this document? You will no longer have access to it.'
    } else {
      setError('You do not have permission to delete or leave this document')
      return
    }
    
    if (!window.confirm(confirmMessage)) {
      return
    }
    
    try {
      const response = await documentService.deleteDocument(id, user.id)
      setDocuments(documents.filter(doc => doc.id !== id))
      setFilteredDocuments(filteredDocuments.filter(doc => doc.id !== id))
      setError('') // Clear any previous errors
      // Show success message
      if (response?.message) {
        alert(response.message)
      } else if (isCollaborator) {
        alert('You have left the document')
      } else {
        alert('Document deleted successfully')
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete/leave document')
    }
  }

  const handleRemoveCollaborator = async (docId, collaboratorId) => {
    if (!window.confirm('Are you sure you want to remove this collaborator?')) {
      return
    }
    try {
      await documentService.removeCollaborator(docId, user.id, collaboratorId)
      loadDocuments() // Reload to refresh collaborator list
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to remove collaborator')
    }
  }

  const handleFileUpload = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    
    const fileExtension = file.name.split('.').pop().toLowerCase()
    if (fileExtension !== 'docx' && fileExtension !== 'txt') {
      setError('Only .docx and .txt files are allowed')
      setTimeout(() => setError(''), 3000)
      return
    }
    
    try {
      setLoading(true)
      setError('')
      const uploadedDoc = await documentService.uploadDocument(file, user.id, null)
      navigate(`/document/${uploadedDoc.id}`)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to upload document')
      setTimeout(() => setError(''), 5000)
    } finally {
      setLoading(false)
      // Reset file input
      e.target.value = ''
    }
  }

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A'
    return new Date(dateString).toLocaleDateString()
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="header-content">
          <h1>Collaborative Editing System</h1>
          <div className="user-info">
            <span>Welcome, {user.username}!</span>
            <button 
              onClick={() => navigate('/settings')} 
              className="btn btn-secondary"
            >
              Settings
            </button>
            <button onClick={onLogout} className="btn btn-secondary">Logout</button>
          </div>
        </div>
      </header>

      <div className="container">
        {error && <div className="error">{error}</div>}
        
        <div className="dashboard-actions">
          <div className="search-container">
            <input
              type="text"
              className="search-input"
              placeholder="Search documents..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            {isSearching && <span className="search-loading">Searching...</span>}
          </div>
          <button 
            onClick={() => setShowCreateModal(true)} 
            className="btn btn-primary"
          >
            + Create New Document
          </button>
          <button 
            onClick={() => document.getElementById('file-upload-input')?.click()} 
            className="btn btn-secondary"
            title="Upload Word or Text document"
          >
            📄 Upload Document
          </button>
          <input
            id="file-upload-input"
            type="file"
            accept=".docx,.txt"
            style={{ display: 'none' }}
            onChange={handleFileUpload}
          />
          {user.isAdmin && (
            <button 
              onClick={() => navigate('/admin')} 
              className="btn btn-secondary"
            >
              Admin Panel
            </button>
          )}
        </div>

        {loading ? (
          <div className="loading">Loading documents...</div>
        ) : (
          <div className="documents-grid">
            {filteredDocuments.length === 0 ? (
              <div className="empty-state">
                <p>{searchQuery ? 'No documents found matching your search.' : 'No documents yet. Create your first document!'}</p>
              </div>
            ) : (
              filteredDocuments.map(doc => (
                <div key={doc.id} className="document-card">
                  <div className="document-header">
                    <h3 onClick={() => navigate(`/document/${doc.id}`)}>
                      {doc.title || 'Untitled Document'}
                    </h3>
                    <div className="document-actions">
                      {doc.ownerId === user.id ? (
                        <button
                          onClick={() => handleDeleteDocument(doc.id, doc)}
                          className="btn btn-danger btn-sm"
                        >
                          Delete
                        </button>
                      ) : (
                        <button
                          onClick={() => handleDeleteDocument(doc.id, doc)}
                          className="btn btn-warning btn-sm"
                        >
                          Leave
                        </button>
                      )}
                    </div>
                  </div>
                  <p className="document-meta">
                    Document ID: <strong>{doc.id}</strong> | 
                    Owner ID: {doc.ownerId} | 
                    Updated: {formatDate(doc.updatedAt)}
                  </p>
                  {doc.collaboratorIds && doc.collaboratorIds.length > 0 && doc.ownerId === user.id && (
                    <div className="document-collaborators">
                      <p>{doc.collaboratorIds.length} collaborator(s)</p>
                      <div className="collaborator-list">
                        {doc.collaboratorIds.map(collabId => (
                          <span key={collabId} className="collaborator-tag">
                            {collaboratorUsernames[collabId] || `User ${collabId}`}
                            <button
                              onClick={() => handleRemoveCollaborator(doc.id, collabId)}
                              className="btn-remove-collab"
                              title="Remove collaborator"
                            >
                              ×
                            </button>
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {doc.collaboratorIds && doc.collaboratorIds.length > 0 && doc.ownerId !== user.id && (
                    <p className="document-collaborators">
                      Shared with you (Owner: {ownerUsernames[doc.ownerId] || `User ${doc.ownerId}`})
                    </p>
                  )}
                  <div className="document-preview">
                    {doc.content ? (
                      <p>{doc.content.substring(0, 100)}...</p>
                    ) : (
                      <p className="text-muted">No content</p>
                    )}
                  </div>
                  <button
                    onClick={() => navigate(`/document/${doc.id}`)}
                    className="btn btn-primary btn-sm"
                  >
                    Open
                  </button>
                </div>
              ))
            )}
          </div>
        )}

        {showCreateModal && (
          <div className="modal" onClick={() => setShowCreateModal(false)}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <h2>Create New Document</h2>
              <form onSubmit={handleCreateDocument}>
                <input
                  type="text"
                  placeholder="Document Title"
                  value={newDocument.title}
                  onChange={(e) => setNewDocument({ ...newDocument, title: e.target.value })}
                  required
                  className="input"
                />
                <textarea
                  placeholder="Document Content (optional)"
                  value={newDocument.content}
                  onChange={(e) => setNewDocument({ ...newDocument, content: e.target.value })}
                  className="input"
                  rows="5"
                />
                <div className="modal-actions">
                  <button type="submit" className="btn btn-primary">Create</button>
                  <button
                    type="button"
                    onClick={() => setShowCreateModal(false)}
                    className="btn btn-secondary"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default Dashboard
