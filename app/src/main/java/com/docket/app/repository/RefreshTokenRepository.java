package com.docket.app.repository;

import com.docket.app.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Locks the row so two simultaneous refreshes of one token run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // The tokens a session can still refresh with, newest first.
    @Query("""
            select t from RefreshToken t
            where t.userId = :userId and t.revokedAt is null and t.rotatedAt is null and t.expiresAt > :now
            order by t.createdAt desc
            """)
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    // A token is issued at each login and about every 15 minutes while someone is using
    // the app, which makes the newest one a good measure of when they were last active.
    @Query("select t.userId, max(t.createdAt) from RefreshToken t where t.userId in :userIds group by t.userId")
    List<Object[]> findLastIssuedByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamilyForUser(@Param("userId") UUID userId, @Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
