// Import polyfills first - CRITICAL for preventing Request errors
import 'whatwg-fetch'

// Ensure Request is available globally before any other imports
if (typeof globalThis.Request === 'undefined') {
  if (typeof window !== 'undefined' && window.Request) {
    globalThis.Request = window.Request
    globalThis.Response = window.Response
    globalThis.Headers = window.Headers
  } else if (typeof self !== 'undefined' && self.Request) {
    globalThis.Request = self.Request
    globalThis.Response = self.Response
    globalThis.Headers = self.Headers
  }
}

// Ensure undici shim is available if it was set by the HTML script
if (typeof globalThis.undici === 'undefined' && typeof window !== 'undefined' && window.undici) {
  globalThis.undici = window.undici
}

// Import undici shim to ensure it's available for any imports
import './polyfills/undici-shim.js'

import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

// Add error boundary for better error handling
try {
  const rootElement = document.getElementById('root')
  if (!rootElement) {
    throw new Error('Root element not found')
  }
  
  const root = ReactDOM.createRoot(rootElement)
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  )
} catch (error) {
  console.error('Failed to render app:', error)
  const rootElement = document.getElementById('root')
  if (rootElement) {
    rootElement.innerHTML = `
      <div style="padding: 20px; font-family: Arial, sans-serif;">
        <h1>Application Error</h1>
        <p>Failed to load the application. Please check the browser console for details.</p>
        <p style="color: red;">Error: ${error.message}</p>
        <button onclick="window.location.reload()" style="padding: 10px 20px; margin-top: 10px;">Reload Page</button>
      </div>
    `
  }
}


