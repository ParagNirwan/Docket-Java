package com.docket.app.entity;

import com.docket.app.enums.AccountTokenPurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// A single-use token sent by email, for resetting a password or verifying an address.
@Entity
@Table(name = "account_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountTokenPurpose purpose;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
