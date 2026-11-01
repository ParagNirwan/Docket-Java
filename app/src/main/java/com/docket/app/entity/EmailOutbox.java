package com.docket.app.entity;

import com.docket.app.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// An email waiting to be sent, or the record of one that finished.
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String toAddress;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text")
    private String htmlBody;

    @Column(columnDefinition = "text")
    private String textBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;
}
