package com.docket.app.repository;

import com.docket.app.entity.TenantUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUsageRepository extends JpaRepository<TenantUsage, UUID> {
    Optional<TenantUsage> findByTenantId(UUID tenantId);

    @Modifying
    @Query("""
        UPDATE TenantUsage u
        SET u.storageBytesUsed = GREATEST(0L, u.storageBytesUsed + :deltaBytes),
            u.documentCount = GREATEST(0L, u.documentCount + :deltaDocs),
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.tenantId = :tenantId
    """)
    int adjustUsage(@Param("tenantId") UUID tenantId,
                    @Param("deltaBytes") long deltaBytes,
                    @Param("deltaDocs") long deltaDocs);
}

