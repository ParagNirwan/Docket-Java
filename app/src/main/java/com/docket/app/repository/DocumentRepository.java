package com.docket.app.repository;

import com.docket.app.entity.Document;
import com.docket.app.enums.FileStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // Find all active documents in a specific folder, with pagination.
    // Used by the "list documents" endpoint.
    Page<Document> findByTenantIdAndFolderIdAndFileStatus(UUID tenantId, UUID folderId, FileStatus status, Pageable page);

    // Find one specific document by its ID, but only if it belongs to
    // the given tenant. This prevents users in one organization from
    // accessing documents that belong to a different organization.
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);
}
