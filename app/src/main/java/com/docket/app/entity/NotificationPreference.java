package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Someone without a row has the defaults: in-app and email on, digest off.
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {
    @Id
    private UUID userId;
    private boolean inApp = true;
    private boolean email = true;
    private boolean dailyDigest = false;
}
