import axios from 'axios'

// Use relative path for API - will be proxied by Vite dev server or nginx in production
// This works in both development and production
const API_BASE_URL = '/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 60000, // 60 second timeout (increased for slow startup)
})

// Request interceptor to add JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Add response interceptor for error handling and token expiration
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle token expiration (401 Unauthorized)
    if (error.response && error.response.status === 401) {
      const errorData = error.response.data
      const errorMessage = errorData?.error || 'Unauthorized'
      
      // If token expired, clear auth and redirect to login
      if (errorMessage.includes('expired') || errorMessage.includes('token') || errorMessage.includes('invalid')) {
        localStorage.removeItem('auth_token')
        localStorage.removeItem('user')
        localStorage.removeItem('token_expiry')
        localStorage.removeItem('last_activity')
        
        // Redirect to login if not already there
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        
        error.message = 'Your session has expired. Please login again.'
        return Promise.reject(error)
      }
    }
    
    // Handle network errors (timeout, connection refused, etc.)
    if (!error.response) {
      console.error('Network error:', error.message)
      console.error('Error code:', error.code)
      console.error('Error config:', error.config)
      
      // Provide more specific error messages
      if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
        error.message = 'Request timed out. Please ensure the API Gateway (port 8080) and User Management Service (port 8081) are running.'
      } else if (error.code === 'ERR_NETWORK' || error.code === 'ECONNREFUSED') {
        error.message = 'Cannot connect to server. Please ensure all services are running: API Gateway (8080), User Management (8081).'
      } else {
        error.message = 'Network error. Please check your connection and ensure all services are running.'
      }
    } else {
      // Log server errors for debugging
      if (error.response.status >= 500) {
        console.error('Server error:', error.response.status, error.response.data)
        const errorData = error.response.data
        error.message = errorData?.error || errorData?.message || `Server error (${error.response.status}). Please try again later.`
        // Preserve the full error data for better error handling
        if (errorData?.cause) {
          error.message += `: ${errorData.cause}`
        }
      } else if (error.response.status === 403) {
        console.error('Forbidden:', error.response.data)
        error.message = error.response.data?.error || 'Access forbidden. Please check your permissions.'
      } else if (error.response.status === 404) {
        console.error('Not found:', error.response.data)
        error.message = error.response.data?.error || 'Resource not found.'
      }
    }
    return Promise.reject(error)
  }
)

export default api

