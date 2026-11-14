package com.docket.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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
        private static final long serialVersionUID = 1L;
        private UUID trayId;
        private UUID userId;
    }
}

