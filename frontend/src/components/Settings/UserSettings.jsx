import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { authService } from '../../services/authService'
import './UserSettings.css'

const UserSettings = ({ user, onUserUpdate }) => {
  const navigate = useNavigate()
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    username: ''
  })
  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [passwordSuccess, setPasswordSuccess] = useState('')
  const [changingPassword, setChangingPassword] = useState(false)

  useEffect(() => {
    if (user) {
      setFormData({
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        email: user.email || '',
        username: user.username || ''
      })
    }
  }, [user])

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    setLoading(true)

    try {
      // Ensure username is always included (required by backend validation)
      const updateData = {
        ...formData,
        username: formData.username || user.username
      }
      const updatedUser = await authService.updateProfile(user.id, updateData)
      setSuccess('Profile updated successfully!')
      
      // Update user in parent component
      const updatedUserData = {
        ...user,
        ...updatedUser
      }
      onUserUpdate(updatedUserData)
      
      // Update localStorage
      localStorage.setItem('user', JSON.stringify(updatedUserData))
    } catch (err) {
      console.error('Update error:', err)
      let errorMessage = 'Failed to update profile'
      
      if (err.response?.data) {
        const errorData = err.response.data
        // Check for validation errors
        if (errorData.details) {
          const validationErrors = Object.values(errorData.details).join(', ')
          errorMessage = `Validation error: ${validationErrors}`
        } else if (errorData.message) {
          errorMessage = errorData.message
        } else if (errorData.error) {
          errorMessage = errorData.error
        }
      } else if (err.message) {
        errorMessage = err.message
      }
      
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteAccount = async () => {
    const confirmed = window.confirm(
      'Are you sure you want to delete your account? This action cannot be undone. All your documents will be deleted.'
    )
    
    if (!confirmed) return

    try {
      setLoading(true)
      await authService.deleteAccount(user.id)
      // Immediately clear session and logout before navigation
      authService.logout()
      // Clear any remaining state
      localStorage.clear()
      // Navigate to login immediately
      window.location.href = '/login'
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete account')
      setLoading(false)
    }
  }

  const handlePasswordChange = (e) => {
    setPasswordData({
      ...passwordData,
      [e.target.name]: e.target.value
    })
    setPasswordError('')
    setPasswordSuccess('')
  }

  const handlePasswordSubmit = async (e) => {
    e.preventDefault()
    setPasswordError('')
    setPasswordSuccess('')
    
    if (!passwordData.currentPassword || !passwordData.newPassword || !passwordData.confirmPassword) {
      setPasswordError('All password fields are required')
      return
    }
    
    if (passwordData.newPassword.length < 6) {
      setPasswordError('New password must be at least 6 characters long')
      return
    }
    
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      setPasswordError('New passwords do not match')
      return
    }
    
    if (passwordData.currentPassword === passwordData.newPassword) {
      setPasswordError('New password must be different from current password')
      return
    }

    try {
      setChangingPassword(true)
      // Verify current password by attempting to login (this will fail if password is wrong)
      try {
        await authService.login(user.username, passwordData.currentPassword)
        // If login succeeds, current password is correct
        // Note: This creates a new session, but we'll continue with the password update
      } catch (err) {
        setPasswordError('Current password is incorrect')
        setChangingPassword(false)
        return
      }
      
      // If current password is correct, update to new password
      // Note: Backend should ideally verify current password, but for now we verify in frontend
      const updateData = {
        password: passwordData.newPassword
      }
      await authService.updateProfile(user.id, updateData)
      setPasswordSuccess('Password changed successfully! Please login again with your new password.')
      setPasswordData({
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      })
      // Optionally logout and ask user to login again with new password
      // Uncomment the following lines if you want to force re-login after password change:
      // setTimeout(() => {
      //   authService.logout()
      //   navigate('/login')
      // }, 2000)
    } catch (err) {
      console.error('Password change error:', err)
      setPasswordError(err.response?.data?.error || err.response?.data?.message || 'Failed to change password')
    } finally {
      setChangingPassword(false)
    }
  }

  return (
    <div className="user-settings">
      <div className="settings-container">
        <div className="settings-header">
          <h1>User Settings</h1>
          <button onClick={() => navigate('/dashboard')} className="btn btn-secondary">
            Back to Dashboard
          </button>
        </div>

        <div className="settings-content">
          <div className="user-info-section">
            <h2>Account Information</h2>
            <div className="info-item">
              <label>User ID:</label>
              <span className="user-id">{user?.id || 'N/A'}</span>
            </div>
            <div className="info-item">
              <label>Username:</label>
              <span>{user?.username || 'N/A'}</span>
            </div>
          </div>

          {error && <div className="error">{error}</div>}
          {success && <div className="success">{success}</div>}

          <form onSubmit={handleSubmit} className="settings-form">
            <h2>Update Profile</h2>
            
            <div className="form-group">
              <label htmlFor="firstName">First Name</label>
              <input
                type="text"
                id="firstName"
                name="firstName"
                value={formData.firstName}
                onChange={handleChange}
                className="input"
                placeholder="First Name"
              />
            </div>

            <div className="form-group">
              <label htmlFor="lastName">Last Name</label>
              <input
                type="text"
                id="lastName"
                name="lastName"
                value={formData.lastName}
                onChange={handleChange}
                className="input"
                placeholder="Last Name"
              />
            </div>

            <div className="form-group">
              <label htmlFor="username">Username</label>
              <input
                type="text"
                id="username"
                name="username"
                value={formData.username}
                onChange={handleChange}
                className="input"
                placeholder="Username"
                required
                minLength={3}
              />
            </div>

            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="input"
                placeholder="Email"
                required
              />
            </div>

            <div className="form-actions">
              <button 
                type="submit" 
                className="btn btn-primary" 
                disabled={loading}
              >
                {loading ? 'Updating...' : 'Update Profile'}
              </button>
            </div>
          </form>

          <form onSubmit={handlePasswordSubmit} className="settings-form">
            <h2>Change Password</h2>
            
            {passwordError && <div className="error">{passwordError}</div>}
            {passwordSuccess && <div className="success">{passwordSuccess}</div>}
            
            <div className="form-group">
              <label htmlFor="currentPassword">Current Password</label>
              <input
                type="password"
                id="currentPassword"
                name="currentPassword"
                value={passwordData.currentPassword}
                onChange={handlePasswordChange}
                className="input"
                placeholder="Enter current password"
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="newPassword">New Password</label>
              <input
                type="password"
                id="newPassword"
                name="newPassword"
                value={passwordData.newPassword}
                onChange={handlePasswordChange}
                className="input"
                placeholder="Enter new password (min 6 characters)"
                required
                minLength={6}
              />
            </div>

            <div className="form-group">
              <label htmlFor="confirmPassword">Confirm New Password</label>
              <input
                type="password"
                id="confirmPassword"
                name="confirmPassword"
                value={passwordData.confirmPassword}
                onChange={handlePasswordChange}
                className="input"
                placeholder="Confirm new password"
                required
                minLength={6}
              />
            </div>

            <div className="form-actions">
              <button 
                type="submit" 
                className="btn btn-primary" 
                disabled={changingPassword}
              >
                {changingPassword ? 'Changing...' : 'Change Password'}
              </button>
            </div>
          </form>

          <div className="danger-zone">
            <h2>Danger Zone</h2>
            <div className="danger-actions">
              <button
                onClick={handleDeleteAccount}
                className="btn btn-danger"
                disabled={loading}
              >
                Delete My Account
              </button>
              <p className="danger-warning">
                This will permanently delete your account. This action cannot be undone.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default UserSettings

