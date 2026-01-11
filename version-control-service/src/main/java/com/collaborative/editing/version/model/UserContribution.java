package com.collaborative.editing.version.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_contributions")
public class UserContribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long documentId;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private Integer editCount = 0;
    
    @Column(nullable = false)
    private Integer charactersAdded = 0;
    
    @Column(nullable = false)
    private Integer charactersDeleted = 0;
    
    @Column(nullable = false)
    private LocalDateTime firstContribution;
    
    private LocalDateTime lastContribution;

    public UserContribution() {
        this.firstContribution = LocalDateTime.now();
        this.lastContribution = LocalDateTime.now();
    }

    public UserContribution(Long documentId, Long userId) {
        this();
        this.documentId = documentId;
        this.userId = userId;
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

    public Integer getEditCount() {
        return editCount;
    }

    public void setEditCount(Integer editCount) {
        this.editCount = editCount;
    }

    public Integer getCharactersAdded() {
        return charactersAdded;
    }

    public void setCharactersAdded(Integer charactersAdded) {
        this.charactersAdded = charactersAdded;
    }

    public Integer getCharactersDeleted() {
        return charactersDeleted;
    }

    public void setCharactersDeleted(Integer charactersDeleted) {
        this.charactersDeleted = charactersDeleted;
    }

    public LocalDateTime getFirstContribution() {
        return firstContribution;
    }

    public void setFirstContribution(LocalDateTime firstContribution) {
        this.firstContribution = firstContribution;
    }

    public LocalDateTime getLastContribution() {
        return lastContribution;
    }

    public void setLastContribution(LocalDateTime lastContribution) {
        this.lastContribution = lastContribution;
    }
}

