import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { versionService } from '../../services/versionService'
import './DocumentDiffView.css'

const DocumentDiffView = ({ user }) => {
  const { id: documentId, versionNumber } = useParams()
  const navigate = useNavigate()
  const [diff, setDiff] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    loadDiff()
  }, [documentId, versionNumber])

  const loadDiff = async () => {
    try {
      setLoading(true)
      setError('')
      const diffData = await versionService.getVersionDiff(documentId, parseInt(versionNumber))
      setDiff(diffData)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load diff')
      console.error('Error loading diff:', err)
    } finally {
      setLoading(false)
    }
  }

  const renderDiffContent = () => {
    if (!diff || !diff.segments) return null

    let oldLineNumber = diff.fromVersion !== null && diff.fromVersion !== undefined ? 1 : 0
    let newLineNumber = 1

    return diff.segments.map((segment, index) => {
      const lines = segment.content.split('\n')
      const isAdded = segment.type === 'ADDED'
      const isRemoved = segment.type === 'REMOVED'
      const isUnchanged = segment.type === 'UNCHANGED'
      // Only show attribution for ADDED/REMOVED segments that have a username
      // UNCHANGED segments should not show attribution (they represent existing content)
      const username = segment.username || (segment.userId ? `User ${segment.userId}` : null)
      const showUserAttribution = (isAdded || isRemoved) && username && !isUnchanged

      return (
        <div
          key={index}
          className={`diff-segment ${isAdded ? 'added' : isRemoved ? 'removed' : 'unchanged'}`}
        >
          {showUserAttribution && (
            <div className={`diff-segment-header ${isAdded ? 'header-added' : 'header-removed'}`}>
              <span className="user-attribution">
                {isAdded ? '➕' : '➖'} {username}
              </span>
            </div>
          )}
          {lines.map((line, lineIndex) => {
            let oldNum = ''
            let newNum = ''
            
            if (isRemoved) {
              oldNum = oldLineNumber.toString()
              oldLineNumber++
            } else if (isAdded) {
              newNum = newLineNumber.toString()
              newLineNumber++
            } else {
              oldNum = oldLineNumber.toString()
              newNum = newLineNumber.toString()
              oldLineNumber++
              newLineNumber++
            }

            return (
              <div
                key={lineIndex}
                className={`diff-line ${isAdded ? 'line-added' : isRemoved ? 'line-removed' : 'line-unchanged'}`}
              >
                <span className="line-number old-line">{oldNum}</span>
                <span className="line-number new-line">{newNum}</span>
                <span className="line-content">
                  {line || '\u00A0'} {/* Non-breaking space for empty lines */}
                </span>
              </div>
            )
          })}
        </div>
      )
    })
  }

  if (loading) {
    return (
      <div className="diff-view-container">
        <div className="loading">Loading diff...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="diff-view-container">
        <div className="error">{error}</div>
        <button onClick={() => navigate(`/document/${documentId}`)} className="btn btn-primary">
          Back to Document
        </button>
      </div>
    )
  }

  if (!diff) {
    return (
      <div className="diff-view-container">
        <div className="error">No diff data available</div>
        <button onClick={() => navigate(`/document/${documentId}`)} className="btn btn-primary">
          Back to Document
        </button>
      </div>
    )
  }

  const stats = diff.stats || {}
  const addedLines = stats.addedLines || 0
  const removedLines = stats.removedLines || 0
  const netChange = stats.netChange || 0

  return (
    <div className="diff-view-container">
      <div className="diff-header">
        <button onClick={() => navigate(`/document/${documentId}`)} className="btn btn-secondary">
          ← Back to Document
        </button>
        <h2>Version {diff.toVersion} Diff</h2>
        {diff.fromVersion !== null && diff.fromVersion !== undefined && (
          <p className="diff-subtitle">
            Comparing version {diff.fromVersion} → {diff.toVersion}
          </p>
        )}
        <div className="diff-stats">
          <span className="stat-added">+{addedLines} lines added</span>
          <span className="stat-removed">-{removedLines} lines removed</span>
          {netChange !== 0 && (
            <span className={`stat-net ${netChange > 0 ? 'positive' : 'negative'}`}>
              {netChange > 0 ? '+' : ''}{netChange} characters
            </span>
          )}
        </div>
      </div>

      <div className="diff-content">
        <div className="diff-lines">
          {renderDiffContent()}
        </div>
      </div>
    </div>
  )
}

export default DocumentDiffView
