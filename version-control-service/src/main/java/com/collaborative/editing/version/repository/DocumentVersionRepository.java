package com.collaborative.editing.version.repository;

import com.collaborative.editing.version.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Long versionNumber);
    Long countByDocumentId(Long documentId);
    
    // Get the latest version for a document
    // Use default method to avoid unique constraint issues with findFirst
    default Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(Long documentId) {
        List<DocumentVersion> versions = findByDocumentIdOrderByVersionNumberDesc(documentId);
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(0));
    }
}

