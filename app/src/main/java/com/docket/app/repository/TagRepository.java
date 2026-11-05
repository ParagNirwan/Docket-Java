package com.docket.app.repository;

import com.docket.app.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    List<Tag> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<Tag> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}

