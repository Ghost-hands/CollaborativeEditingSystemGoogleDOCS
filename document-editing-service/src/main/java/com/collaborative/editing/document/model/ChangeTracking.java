package com.collaborative.editing.document.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "change_tracking")
public class ChangeTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long documentId;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String changeType; // INSERT, DELETE, UPDATE
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private Integer position;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = true)
    private Long versionId; // Links change to a specific version (null until version is created)

    public ChangeTracking() {
        this.timestamp = LocalDateTime.now();
    }

    public ChangeTracking(Long documentId, Long userId, String changeType, String content, Integer position) {
        this();
        this.documentId = documentId;
        this.userId = userId;
        this.changeType = changeType;
        this.content = content;
        this.position = position;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
}

