package com.docket.app.repository;

import com.docket.app.entity.AccountToken;
import com.docket.app.enums.AccountTokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    // Locks the row so a token can't be used twice by two simultaneous requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    // Only the most recent email's link should work.
    @Modifying
    @Query("""
            update AccountToken t set t.consumedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int consumeOutstanding(
            @Param("userId") UUID userId,
            @Param("purpose") AccountTokenPurpose purpose,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from AccountToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
