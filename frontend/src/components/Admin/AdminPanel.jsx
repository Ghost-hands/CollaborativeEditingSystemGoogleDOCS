import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { authService } from '../../services/authService'
import { documentService } from '../../services/documentService'
import './AdminPanel.css'

const AdminPanel = ({ user, onLogout }) => {
  const navigate = useNavigate()
  const [users, setUsers] = useState([])
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [documentsLoading, setDocumentsLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [activeTab, setActiveTab] = useState('users') // 'users' or 'documents'

  useEffect(() => {
    if (!user || !user.isAdmin) {
      navigate('/dashboard')
      return
    }
    loadUsers()
    loadAllDocuments()
  }, [user, navigate])

  const loadUsers = async () => {
    try {
      setLoading(true)
      setError('')
      const allUsers = await authService.getAllUsers(user.id)
      setUsers(allUsers)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load users')
      console.error('Error loading users:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteUser = async (userId, username) => {
    if (userId === user.id) {
      setError('You cannot delete your own account from the admin panel')
      return
    }

    const confirmed = window.confirm(
      `Are you sure you want to delete user "${username}" (ID: ${userId})? This action cannot be undone.`
    )

    if (!confirmed) return

    try {
      setError('')
      setSuccess('')
      await authService.adminDeleteUser(userId, user.id)
      setSuccess(`User "${username}" deleted successfully`)
      loadUsers() // Reload users list
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete user')
    }
  }

  const loadAllDocuments = async () => {
    try {
      setDocumentsLoading(true)
      setError('')
      const allDocuments = await documentService.getAllDocumentsForAdmin(user.id)
      setDocuments(allDocuments)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load documents')
      console.error('Error loading documents:', err)
    } finally {
      setDocumentsLoading(false)
    }
  }

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A'
    return new Date(dateString).toLocaleString()
  }

  return (
    <div className="admin-panel">
      <header className="admin-header">
        <div className="header-content">
          <h1>Admin Panel</h1>
          <div className="admin-actions">
            <button 
              onClick={() => navigate('/dashboard')} 
              className="btn btn-secondary"
            >
              Back to Dashboard
            </button>
            <button onClick={onLogout} className="btn btn-secondary">Logout</button>
          </div>
        </div>
      </header>

      <div className="container">
        {error && <div className="error">{error}</div>}
        {success && <div className="success">{success}</div>}

        <div className="admin-content">
          <div className="admin-stats">
            <div className="stat-card">
              <h3>Total Users</h3>
              <p className="stat-number">{users.length}</p>
            </div>
            <div className="stat-card">
              <h3>Active Users</h3>
              <p className="stat-number">{users.length}</p>
            </div>
            <div className="stat-card">
              <h3>Admins</h3>
              <p className="stat-number">
                {users.filter(u => u.isAdmin).length}
              </p>
            </div>
            <div className="stat-card">
              <h3>Total Documents</h3>
              <p className="stat-number">{documents.length}</p>
            </div>
          </div>

          <div className="admin-tabs" style={{ marginBottom: '20px', borderBottom: '2px solid #ddd' }}>
            <button
              onClick={() => setActiveTab('users')}
              className={`btn ${activeTab === 'users' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ marginRight: '10px' }}
            >
              Users
            </button>
            <button
              onClick={() => setActiveTab('documents')}
              className={`btn ${activeTab === 'documents' ? 'btn-primary' : 'btn-secondary'}`}
            >
              All Documents
            </button>
          </div>

          {activeTab === 'users' && (
          <div className="users-section">
            <h2>User Management</h2>
            {loading ? (
              <div className="loading">Loading users...</div>
            ) : (
              <div className="users-table-container">
                <table className="users-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Username</th>
                      <th>Email</th>
                      <th>Name</th>
                      <th>Status</th>
                      <th>Role</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.length === 0 ? (
                      <tr>
                        <td colSpan="7" className="empty-state">
                          No users found
                        </td>
                      </tr>
                    ) : (
                      users.map(u => (
                        <tr key={u.id} className={u.id === user.id ? 'current-user' : ''}>
                          <td>{u.id}</td>
                          <td>{u.username}</td>
                          <td>{u.email}</td>
                          <td>
                            {u.firstName || u.lastName
                              ? `${u.firstName || ''} ${u.lastName || ''}`.trim()
                              : 'N/A'}
                          </td>
                          <td>
                            <span className="status-badge active">
                              Active
                            </span>
                          </td>
                          <td>
                            {u.isAdmin ? (
                              <span className="role-badge admin">Admin</span>
                            ) : (
                              <span className="role-badge user">User</span>
                            )}
                          </td>
                          <td>
                            {u.id === user.id ? (
                              <span className="text-muted">Current User</span>
                            ) : (
                              <button
                                onClick={() => handleDeleteUser(u.id, u.username)}
                                className="btn btn-danger btn-sm"
                              >
                                Delete
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          )}

          {activeTab === 'documents' && (
            <div className="documents-section">
              <h2>All Documents</h2>
              {documentsLoading ? (
                <div className="loading">Loading documents...</div>
              ) : (
                <div className="documents-table-container">
                  <table className="users-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Title</th>
                        <th>Owner ID</th>
                        <th>Collaborators</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th>Updated</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {documents.length === 0 ? (
                        <tr>
                          <td colSpan="8" className="empty-state">
                            No documents found
                          </td>
                        </tr>
                      ) : (
                        documents.map(doc => (
                          <tr key={doc.id}>
                            <td>{doc.id}</td>
                            <td>{doc.title || 'Untitled'}</td>
                            <td>{doc.ownerId}</td>
                            <td>
                              {doc.collaboratorIds && doc.collaboratorIds.length > 0
                                ? `${doc.collaboratorIds.length} collaborator(s)`
                                : 'None'}
                            </td>
                            <td>
                              <span className={`status-badge ${doc.status === 'ACTIVE' ? 'active' : 'inactive'}`}>
                                {doc.status || 'ACTIVE'}
                              </span>
                            </td>
                            <td>{formatDate(doc.createdAt)}</td>
                            <td>{formatDate(doc.updatedAt)}</td>
                            <td>
                              <button
                                onClick={() => navigate(`/document/${doc.id}`)}
                                className="btn btn-primary btn-sm"
                              >
                                View
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default AdminPanel

