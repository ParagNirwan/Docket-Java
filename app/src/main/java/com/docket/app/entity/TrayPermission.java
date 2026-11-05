package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tray_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TrayPermission.TrayPermissionId.class)
public class TrayPermission {
    @Id
    @Column(name = "tray_id")
    private UUID trayId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private Instant createdAt = Instant.now();

    public TrayPermission(UUID trayId, UUID userId) {
        this.trayId = trayId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class TrayPermissionId implements Serializable {
        private UUID trayId;
        private UUID userId;
    }
}

