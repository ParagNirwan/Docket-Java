package com.docket.app.repository;

import com.docket.app.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersion(UUID documentId, Integer version);
}
