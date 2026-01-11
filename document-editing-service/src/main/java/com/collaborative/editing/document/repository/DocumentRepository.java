package com.collaborative.editing.document.repository;

import com.collaborative.editing.document.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerId(Long ownerId);
    List<Document> findByStatus(String status);
    
    // Find documents where user is a collaborator (using native query for ElementCollection)
    @Query(value = "SELECT DISTINCT d.* FROM documents d " +
                   "INNER JOIN document_collaborators dc ON d.id = dc.document_id " +
                   "WHERE dc.collaborator_id = :userId AND d.status = 'ACTIVE'", 
           nativeQuery = true)
    List<Document> findDocumentsByCollaboratorId(@Param("userId") Long userId);
}

