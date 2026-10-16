package com.docket.app.repository;

import com.docket.app.entity.TenantMembership;
import com.docket.app.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Ways to search for membership records in the database.
// Spring automatically turns these method names into database queries.
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    // "Give me all memberships for this person" — every org they've ever been in.
    List<TenantMembership> findByUserId(UUID userId);

    // "Give me this person's memberships, but only active ones."
    // Used when listing which orgs someone belongs to.
    List<TenantMembership> findByUserIdAndStatus(UUID userId, Status status);

    // "Is this specific person an active member of this specific organization?"
    // This is the main question the bouncer asks every time someone tries to do something.
    List<TenantMembership> findByUserIdAndTenantIdAndStatus(
            UUID userId, UUID tenantId, Status status);
}
