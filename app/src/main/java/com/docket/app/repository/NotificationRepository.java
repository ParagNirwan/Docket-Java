package com.docket.app.repository;

import com.docket.app.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdAndInAppIsTrue(UUID userId, Pageable pageable);

    long countByUserIdAndInAppIsTrueAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.inApp = true AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT DISTINCT n.userId FROM Notification n WHERE n.digestPending = true")
    List<UUID> findUsersWithPendingDigest();

    List<Notification> findByUserIdAndDigestPendingIsTrueOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.digestPending = false WHERE n.userId = :userId AND n.digestPending = true")
    int clearPendingDigest(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
