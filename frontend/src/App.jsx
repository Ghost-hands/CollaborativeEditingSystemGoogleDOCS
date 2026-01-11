import React, { useState, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Login from './components/Auth/Login'
import Register from './components/Auth/Register'
import Dashboard from './components/Dashboard/Dashboard'
import DocumentEditor from './components/Document/DocumentEditor'
import DocumentDiffView from './components/Document/DocumentDiffView'
import UserSettings from './components/Settings/UserSettings'
import AdminPanel from './components/Admin/AdminPanel'
import Forbidden403 from './components/Error/Forbidden403'
import { authService } from './services/authService'
import './App.css'

function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Check if user has valid session
    const storedUser = localStorage.getItem('user')
    const token = authService.getToken()
    
    if (storedUser && token) {
      try {
        // Check if token is expired (5 minutes)
        if (authService.isTokenExpired()) {
          console.log('Token expired, logging out')
          authService.logout()
          setUser(null)
          setLoading(false)
          return
        }
        
        // Check if session is expired (10 minutes of inactivity)
        if (authService.isSessionExpired()) {
          console.log('Session expired due to inactivity, logging out')
          authService.logout()
          setUser(null)
          setLoading(false)
          return
        }
        
        // User is valid, restore session
        setUser(JSON.parse(storedUser))
        authService.setLastActivity() // Update activity on app load
      } catch (e) {
        console.error('Error parsing user data:', e)
        authService.logout()
        setUser(null)
      }
    } else {
      // No valid session
      authService.logout()
      setUser(null)
    }
    setLoading(false)
  }, [])
  
  const handleLogout = () => {
    setUser(null)
    localStorage.removeItem('user')
    authService.logout()
  }
  
  // Track user activity and check for session expiration
  useEffect(() => {
    if (!user) return
    
    // Update activity on user interactions
    const updateActivity = () => {
      authService.setLastActivity()
    }
    
    // Check for token and session expiration more frequently (every 30 seconds)
    // Token expires after 5 minutes, session expires after 10 minutes of inactivity
    const checkSessionInterval = setInterval(() => {
      // First check token expiration (5 minutes) - this is the primary check
      if (authService.isTokenExpired()) {
        console.log('Token expired (5 minutes), logging out')
        handleLogout()
        clearInterval(checkSessionInterval)
        window.location.href = '/login'
        return
      }
      
      // Then check session expiration (10 minutes of inactivity) - secondary check
      if (authService.isSessionExpired()) {
        console.log('Session expired due to inactivity (10 minutes), logging out')
        handleLogout()
        clearInterval(checkSessionInterval)
        window.location.href = '/login'
        return
      }
    }, 30000) // Check every 30 seconds for more responsive expiration
    
    // Track user activity events
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click']
    events.forEach(event => {
      window.addEventListener(event, updateActivity, { passive: true })
    })
    
    return () => {
      clearInterval(checkSessionInterval)
      events.forEach(event => {
        window.removeEventListener(event, updateActivity)
      })
    }
  }, [user])

  const handleLogin = (userData) => {
    setUser(userData)
    // Activity tracking is handled by authService.login()
    // and the useEffect hook will update activity on interactions
  }

  const handleUserUpdate = (updatedUser) => {
    setUser(updatedUser)
    localStorage.setItem('user', JSON.stringify(updatedUser))
  }

  if (loading) {
    return <div className="loading">Loading...</div>
  }

  return (
    <Router>
      <div className="App">
        <Routes>
          <Route 
            path="/login" 
            element={user ? <Navigate to="/dashboard" /> : <Login onLogin={handleLogin} />} 
          />
          <Route 
            path="/register" 
            element={user ? <Navigate to="/dashboard" /> : <Register onLogin={handleLogin} />} 
          />
          <Route 
            path="/dashboard" 
            element={user ? <Dashboard user={user} onLogout={handleLogout} /> : <Navigate to="/login" />} 
          />
          <Route 
            path="/document/:id" 
            element={user ? <DocumentEditor user={user} /> : <Navigate to="/login" />} 
          />
          <Route 
            path="/document/:id/diff/:versionNumber" 
            element={user ? <DocumentDiffView user={user} /> : <Navigate to="/login" />} 
          />
          <Route 
            path="/settings" 
            element={user ? <UserSettings user={user} onUserUpdate={handleUserUpdate} /> : <Navigate to="/login" />} 
          />
          <Route 
            path="/admin" 
            element={user && user.isAdmin ? <AdminPanel user={user} onLogout={handleLogout} /> : <Navigate to="/dashboard" />} 
          />
          <Route 
            path="/403" 
            element={<Forbidden403 />} 
          />
          <Route 
            path="/" 
            element={user ? <Navigate to="/dashboard" /> : <Navigate to="/login" />} 
          />
        </Routes>
      </div>
    </Router>
  )
}

export default App

