package com.docket.app.repository;

import com.docket.app.entity.ExternalShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalShareLinkRepository extends JpaRepository<ExternalShareLink, UUID> {
    Optional<ExternalShareLink> findByTokenHash(String tokenHash);
    List<ExternalShareLink> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    @Modifying
    @Query("UPDATE ExternalShareLink l SET l.downloadCount = l.downloadCount + 1 WHERE l.id = :id")
    int incrementDownloadCount(@Param("id") UUID id);
}

