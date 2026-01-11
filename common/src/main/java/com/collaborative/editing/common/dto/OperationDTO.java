package com.collaborative.editing.common.dto;

import java.util.Objects;

/**
 * Represents a single operation in Operational Transformation
 */
public class OperationDTO {
    public enum Type {
        INSERT, DELETE, RETAIN
    }

    private Type type;
    private String content; // For INSERT operations
    private Integer length; // For DELETE and RETAIN operations
    private Integer position; // Position in the document
    private Long userId;
    private Long documentId;
    private Long operationId; // Unique ID for this operation
    private Long baseVersion; // Version this operation is based on

    public OperationDTO() {}

    public OperationDTO(Type type, String content, Integer length, Integer position, 
                       Long userId, Long documentId, Long operationId, Long baseVersion) {
        this.type = type;
        this.content = content;
        this.length = length;
        this.position = position;
        this.userId = userId;
        this.documentId = documentId;
        this.operationId = operationId;
        this.baseVersion = baseVersion;
    }

    // Factory methods for convenience
    public static OperationDTO insert(String content, Integer position, Long userId, 
                                     Long documentId, Long operationId, Long baseVersion) {
        return new OperationDTO(Type.INSERT, content, null, position, userId, 
                               documentId, operationId, baseVersion);
    }

    public static OperationDTO delete(Integer length, Integer position, Long userId, 
                                     Long documentId, Long operationId, Long baseVersion) {
        return new OperationDTO(Type.DELETE, null, length, position, userId, 
                               documentId, operationId, baseVersion);
    }

    public static OperationDTO retain(Integer length) {
        return new OperationDTO(Type.RETAIN, null, length, null, null, null, null, null);
    }

    // Getters and Setters
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

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

    public Long getOperationId() {
        return operationId;
    }

    public void setOperationId(Long operationId) {
        this.operationId = operationId;
    }

    public Long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Long baseVersion) {
        this.baseVersion = baseVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationDTO that = (OperationDTO) o;
        return type == that.type &&
               Objects.equals(content, that.content) &&
               Objects.equals(length, that.length) &&
               Objects.equals(position, that.position) &&
               Objects.equals(operationId, that.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, content, length, position, operationId);
    }

    @Override
    public String toString() {
        return "OperationDTO{" +
               "type=" + type +
               ", content='" + content + '\'' +
               ", length=" + length +
               ", position=" + position +
               ", operationId=" + operationId +
               '}';
    }
}

