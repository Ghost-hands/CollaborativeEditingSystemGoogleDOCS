import api from './api'
import axios from 'axios'

const TOKEN_KEY = 'auth_token'
const USER_KEY = 'user'
const TOKEN_EXPIRY_KEY = 'token_expiry'
const LAST_ACTIVITY_KEY = 'last_activity'

export const authService = {
  async register(userData) {
    const response = await api.post('/users/register', userData)
    return response.data
  },

  async login(username, password) {
    const response = await api.post('/users/authenticate', { username, password })
    // Backend returns { user: UserDTO, token: string, expiresIn: number }
    const { user: userData, token, expiresIn } = response.data
    
    // Store token and user data
    this.setToken(token)
    this.setLastActivity()
    
    // Calculate token expiry time
    const tokenExpiry = Date.now() + expiresIn
    localStorage.setItem(TOKEN_EXPIRY_KEY, tokenExpiry.toString())
    
    // Return user object for app state
    const user = {
      id: userData.id,
      username: userData.username,
      email: userData.email,
      active: userData.active,
      firstName: userData.firstName,
      lastName: userData.lastName,
      profilePicture: userData.profilePicture,
      isAdmin: userData.isAdmin || false
    }
    
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    return user
  },

  getToken() {
    return localStorage.getItem(TOKEN_KEY)
  },

  setToken(token) {
    localStorage.setItem(TOKEN_KEY, token)
  },

  removeToken() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_EXPIRY_KEY)
  },

  isTokenExpired() {
    const tokenExpiry = localStorage.getItem(TOKEN_EXPIRY_KEY)
    if (!tokenExpiry) return true
    return Date.now() >= parseInt(tokenExpiry)
  },

  setLastActivity() {
    localStorage.setItem(LAST_ACTIVITY_KEY, Date.now().toString())
  },

  getLastActivity() {
    const lastActivity = localStorage.getItem(LAST_ACTIVITY_KEY)
    return lastActivity ? parseInt(lastActivity) : null
  },

  isSessionExpired() {
    const lastActivity = this.getLastActivity()
    if (!lastActivity) return true
    // Session expires after 10 minutes (600000 ms) of inactivity
    // This is separate from token expiration - token expires after 5 minutes
    const SESSION_TIMEOUT = 10 * 60 * 1000 // 10 minutes
    const isExpired = Date.now() - lastActivity >= SESSION_TIMEOUT
    if (isExpired) {
      console.log('Session expired due to inactivity:', {
        lastActivity: new Date(lastActivity).toISOString(),
        now: new Date().toISOString(),
        inactiveTime: Date.now() - lastActivity,
        timeout: SESSION_TIMEOUT
      })
    }
    return isExpired
  },

  async getUserById(id) {
    const response = await api.get(`/users/${id}`)
    return response.data
  },

  async updateProfile(userId, userData) {
    const response = await api.put(`/users/${userId}/profile`, userData)
    return response.data
  },

  logout() {
    // Clear all stored session data
    localStorage.removeItem(USER_KEY)
    this.removeToken()
    localStorage.removeItem(LAST_ACTIVITY_KEY)
  },

  async deleteAccount(userId) {
    const response = await api.delete(`/users/${userId}`)
    return response.data
  },

  async getAllUsers(adminId) {
    const response = await api.get(`/users/admin/all?adminId=${adminId}`)
    return response.data
  },

  async adminDeleteUser(userId, adminId) {
    const response = await api.delete(`/users/admin/${userId}?adminId=${adminId}`)
    return response.data
  },

  async getUsersByIds(userIds) {
    if (!userIds || userIds.length === 0) {
      return []
    }
    // Use batch endpoint if available, otherwise fetch individually
    try {
      const idsParam = userIds.join(',')
      // Use internal endpoint through API gateway (bypass /api base URL)
      const token = this.getToken()
      const headers = token ? { Authorization: `Bearer ${token}` } : {}
      const response = await axios.get(`/internal/users/batch?ids=${idsParam}`, { headers })
      return response.data
    } catch (err) {
      // Fallback: fetch individually
      console.warn('Batch fetch failed, fetching individually:', err)
      const promises = userIds.map(id => this.getUserById(id).catch(() => null))
      const results = await Promise.all(promises)
      return results.filter(user => user !== null)
    }
  }
}

