package com.collaborative.editing.document.repository;

import com.collaborative.editing.document.model.ChangeTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeTrackingRepository extends JpaRepository<ChangeTracking, Long> {
    List<ChangeTracking> findByDocumentIdOrderByTimestampDesc(Long documentId);
    List<ChangeTracking> findByDocumentIdAndUserId(Long documentId, Long userId);
    List<ChangeTracking> findByDocumentIdAndVersionIdIsNullOrderByTimestampAsc(Long documentId);
    List<ChangeTracking> findByVersionIdOrderByTimestampAsc(Long versionId);
    List<ChangeTracking> findByDocumentIdAndVersionIdIsNotNullOrderByTimestampAsc(Long documentId);
}

