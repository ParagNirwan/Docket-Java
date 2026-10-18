package com.docket.app.repository;

import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Ways to search for organizations in the database.
// Spring automatically turns these method names into database queries.
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    // "Find the organization with this URL name." Used when visiting /workspace/acme-corp.
    Optional<Tenant> findBySlug(String slug);

    // "Is this URL name already taken?" Used when creating a new org.
    boolean existsBySlug(String slug);

    // "Does this organization exist and is it active?" Used by the bouncer to
    // make sure the org hasn't been shut down before granting access.
    boolean existsByIdAndStatus(UUID id, TenantStatus status);

}