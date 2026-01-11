package com.collaborative.editing.common.dto;

public class CursorPositionDTO {
    private Long userId;
    private Long documentId;
    private Integer position;
    private String userName; // Optional: for display purposes
    private String color; // Color for the cursor

    public CursorPositionDTO() {}

    public CursorPositionDTO(Long userId, Long documentId, Integer position, String userName, String color) {
        this.userId = userId;
        this.documentId = documentId;
        this.position = position;
        this.userName = userName;
        this.color = color;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}

