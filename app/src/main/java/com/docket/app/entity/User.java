package com.docket.app.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private Instant createdAt;

    // Access tokens carrying a different number than this are rejected.
    @Column(nullable = false)
    private int tokenVersion;

    private Instant emailVerifiedAt;

    @Column(nullable = false)
    private boolean twoFactorEnabled = false;

    private String twoFactorSecret;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> twoFactorBackupCodes = new ArrayList<>();

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
