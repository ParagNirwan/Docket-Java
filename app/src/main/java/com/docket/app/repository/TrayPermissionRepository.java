package com.docket.app.repository;

import com.docket.app.entity.TrayPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrayPermissionRepository extends JpaRepository<TrayPermission, TrayPermission.TrayPermissionId> {
    List<TrayPermission> findByTrayId(UUID trayId);
    boolean existsByTrayIdAndUserId(UUID trayId, UUID userId);

    @Modifying
    @Query("DELETE FROM TrayPermission tp WHERE tp.trayId = :trayId")
    void deleteByTrayId(@Param("trayId") UUID trayId);

    @Query("SELECT tp.userId FROM TrayPermission tp WHERE tp.trayId = :trayId")
    List<UUID> findUserIdsByTrayId(@Param("trayId") UUID trayId);
}

