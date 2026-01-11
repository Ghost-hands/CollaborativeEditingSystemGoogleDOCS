package com.collaborative.editing.common.dto;

import java.time.LocalDateTime;

public class ChangeTrackingDTO {
    private Long id;
    private Long documentId;
    private Long userId;
    private String changeType; // INSERT, DELETE, UPDATE
    private String content;
    private Integer position;
    private LocalDateTime timestamp;

    public ChangeTrackingDTO() {}

    public ChangeTrackingDTO(Long documentId, Long userId, String changeType, String content, Integer position) {
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
}

