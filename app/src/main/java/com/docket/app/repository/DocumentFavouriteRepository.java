package com.docket.app.repository;

import com.docket.app.entity.DocumentFavourite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFavouriteRepository extends JpaRepository<DocumentFavourite, UUID> {
    Optional<DocumentFavourite> findByTenantIdAndUserIdAndDocumentId(UUID tenantId, UUID userId, UUID documentId);

    List<DocumentFavourite> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId, Pageable pageable);

    List<DocumentFavourite> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndDocumentId(UUID tenantId, UUID userId, UUID documentId);

    void deleteByTenantIdAndUserIdAndDocumentId(UUID tenantId, UUID userId, UUID documentId);
}
