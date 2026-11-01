package com.docket.app.repository;

import com.docket.app.entity.TenantMembership;
import com.docket.app.enums.MemberStatus;
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
    List<TenantMembership> findByUserIdAndMemberStatus(UUID userId, MemberStatus memberStatus);

    // "Is this specific person an active member of this specific organization?"
    // This is the main question the bouncer asks every time someone tries to do something.
    List<TenantMembership> findByUserIdAndTenantIdAndMemberStatus(
            UUID userId, UUID tenantId, MemberStatus memberStatus);

    // Every membership this person has had in this org, including ended ones.
    List<TenantMembership> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    // "Who is in this organization?"
    List<TenantMembership> findByTenantIdAndMemberStatus(UUID tenantId, MemberStatus memberStatus);
}
