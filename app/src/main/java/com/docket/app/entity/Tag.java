package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 7)
    private String color;

    private Instant createdAt = Instant.now();

    public Tag(UUID tenantId, String name, String color) {
        this.tenantId = tenantId;
        this.name = name;
        this.color = color;
        this.createdAt = Instant.now();
    }
}

