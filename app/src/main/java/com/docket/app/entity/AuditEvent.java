package com.docket.app.entity;

import com.docket.app.enums.AuditAction;
import com.docket.app.enums.AuditActorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// One line in the audit log: who did what, to what, and from where.
// The database refuses updates to this table, so treat rows as write-once.
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID tenantId;

    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    private String targetType;

    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    private String ip;

    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;
}
