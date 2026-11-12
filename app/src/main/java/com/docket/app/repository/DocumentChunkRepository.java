package com.docket.app.repository;

import com.docket.app.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

        @Transactional
        void deleteByDocumentId(UUID documentId);

        List<DocumentChunk> findByDocumentId(UUID documentId);
}
