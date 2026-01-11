import api from './api'

export const versionService = {
  async createVersion(documentId, content, createdBy, changeDescription) {
    const response = await api.post('/versions', {
      documentId,
      content,
      createdBy,
      changeDescription
    })
    return response.data
  },

  async getVersionHistory(documentId) {
    const response = await api.get(`/versions/document/${documentId}/history`)
    return response.data
  },

  async getVersionByNumber(documentId, versionNumber) {
    const response = await api.get(`/versions/document/${documentId}/version/${versionNumber}`)
    return response.data
  },

  async revertToVersion(documentId, versionNumber, userId) {
    const response = await api.post(
      `/versions/document/${documentId}/revert/${versionNumber}?userId=${userId}`
    )
    return response.data
  },

  async getUserContributions(documentId) {
    const response = await api.get(`/versions/document/${documentId}/contributions`)
    return response.data
  },

  async getChangesForVersion(versionId) {
    const response = await api.get(`/versions/version/${versionId}/changes`)
    return response.data
  },

  async getVersionHistoryWithDiffs(documentId) {
    const response = await api.get(`/versions/document/${documentId}/history/with-diffs`)
    return response.data
  },

  async getVersionDiff(documentId, versionNumber) {
    const response = await api.get(`/versions/document/${documentId}/version/${versionNumber}/diff`)
    return response.data
  },

  async getVersionDiffBetween(documentId, fromVersion, toVersion) {
    const params = new URLSearchParams()
    if (fromVersion !== null && fromVersion !== undefined) {
      params.append('from', fromVersion)
    }
    params.append('to', toVersion)
    const response = await api.get(`/versions/document/${documentId}/diff?${params.toString()}`)
    return response.data
  }
}

