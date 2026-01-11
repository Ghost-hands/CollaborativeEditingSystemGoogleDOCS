import api from './api'

export const documentService = {
  async getAllDocuments(userId = null) {
    const url = userId ? `/documents?userId=${userId}` : '/documents'
    const response = await api.get(url)
    return response.data
  },

  async getDocumentById(id, userId) {
    if (!userId) {
      throw new Error('User ID is required to access documents')
    }
    const response = await api.get(`/documents/${id}?userId=${userId}`)
    console.log('📡 API Response for document:', id, {
      status: response.status,
      data: response.data,
      hasContent: response.data?.content !== undefined && response.data?.content !== null,
      contentLength: response.data?.content ? response.data.content.length : 0,
      contentType: typeof response.data?.content
    })
    return response.data
  },

  async createDocument(documentData) {
    const response = await api.post('/documents', documentData)
    return response.data
  },

  async updateDocument(id, userId, content) {
    const response = await api.put(`/documents/${id}/edit?userId=${userId}`, { content })
    return response.data
  },

  async deleteDocument(id, userId) {
    const response = await api.delete(`/documents/${id}?userId=${userId}`)
    return response.data
  },

  async addCollaborator(documentId, ownerId, collaboratorId) {
    const response = await api.post(
      `/documents/${documentId}/collaborators?ownerId=${ownerId}`,
      { collaboratorId }
    )
    return response.data
  },

  async removeCollaborator(documentId, ownerId, collaboratorId) {
    const response = await api.delete(
      `/documents/${documentId}/collaborators/${collaboratorId}?ownerId=${ownerId}`
    )
    return response.data
  },

  async getDocumentsByOwner(ownerId) {
    const response = await api.get(`/documents/owner/${ownerId}`)
    return response.data
  },

  async getAllDocumentsForAdmin(adminId) {
    const response = await api.get(`/documents?adminId=${adminId}`)
    return response.data
  },

  async uploadDocument(file, ownerId, title = null) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('ownerId', ownerId)
    if (title) {
      formData.append('title', title)
    }
    const response = await api.post('/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response.data
  },

  async exportDocument(id, format, userId) {
    try {
      const response = await api.get(`/documents/${id}/export?format=${format}&userId=${userId}`, {
        responseType: 'blob'
      })
      
      // Check if response is actually an error (JSON error responses come as blobs)
      if (response.data.type && response.data.type.includes('application/json')) {
        // It's an error response, parse it
        const text = await response.data.text()
        const errorData = JSON.parse(text)
        throw new Error(errorData.error || 'Failed to export document')
      }
      
      // Create download link
      const url = window.URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      const contentDisposition = response.headers['content-disposition']
      let filename = `document.${format}`
      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="(.+)"/)
        if (filenameMatch) {
          filename = filenameMatch[1]
        }
      }
      link.setAttribute('download', filename)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      return true
    } catch (error) {
      // If it's a blob error response, try to parse it
      if (error.response && error.response.data instanceof Blob) {
        try {
          const text = await error.response.data.text()
          const errorData = JSON.parse(text)
          throw new Error(errorData.error || 'Failed to export document')
        } catch (parseError) {
          throw new Error('Failed to export document')
        }
      }
      throw error
    }
  },

  async searchDocuments(userId, query) {
    const response = await api.get(`/documents/search?userId=${userId}&query=${encodeURIComponent(query)}`)
    return response.data
  }
}

