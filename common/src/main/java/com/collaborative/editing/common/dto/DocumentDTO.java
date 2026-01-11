package com.collaborative.editing.common.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Set;

public class DocumentDTO {
    private Long id;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String content;
    private Long ownerId;
    private Set<Long> collaboratorIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status; // ACTIVE, ARCHIVED, DELETED

    public DocumentDTO() {}

    public DocumentDTO(Long id, String title, String content, Long ownerId) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.ownerId = ownerId;
        this.status = "ACTIVE";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Set<Long> getCollaboratorIds() {
        return collaboratorIds;
    }

    public void setCollaboratorIds(Set<Long> collaboratorIds) {
        this.collaboratorIds = collaboratorIds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

