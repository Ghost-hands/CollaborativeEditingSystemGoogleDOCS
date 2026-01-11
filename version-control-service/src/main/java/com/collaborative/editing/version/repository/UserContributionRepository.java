package com.collaborative.editing.version.repository;

import com.collaborative.editing.version.model.UserContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserContributionRepository extends JpaRepository<UserContribution, Long> {
    List<UserContribution> findByDocumentId(Long documentId);
    Optional<UserContribution> findByDocumentIdAndUserId(Long documentId, Long userId);
    List<UserContribution> findByUserId(Long userId);
}

