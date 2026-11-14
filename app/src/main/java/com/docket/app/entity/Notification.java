package com.docket.app.entity;

import com.docket.app.enums.NotificationType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// inApp is false for someone who only wants this in their digest: it's kept for the
// digest to pick up, but doesn't show under the bell.
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    private String title;
    private String body;
    private String link;
    private boolean inApp;
    private boolean digestPending;
    private Instant readAt;
    private Instant createdAt;
}
