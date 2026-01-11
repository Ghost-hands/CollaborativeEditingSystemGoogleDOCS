import React from 'react'
import { useNavigate } from 'react-router-dom'
import './Forbidden403.css'

const Forbidden403 = () => {
  const navigate = useNavigate()

  return (
    <div className="forbidden-container">
      <div className="forbidden-content">
        <div className="forbidden-icon">
          <svg width="120" height="120" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <h1 className="forbidden-title">403</h1>
        <h2 className="forbidden-subtitle">Access Forbidden</h2>
        <p className="forbidden-message">
          You do not have permission to access this document.
          <br />
          This document may be private or you may not be a collaborator.
        </p>
        <div className="forbidden-actions">
          <button 
            onClick={() => navigate('/dashboard')} 
            className="btn btn-primary"
          >
            Go to Dashboard
          </button>
          <button 
            onClick={() => navigate(-1)} 
            className="btn btn-secondary"
          >
            Go Back
          </button>
        </div>
      </div>
    </div>
  )
}

export default Forbidden403
