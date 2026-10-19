package com.docket.app.entity;

import com.docket.app.enums.Roles;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// A "membership card" — each row in this table says:
// "This person belongs to this organization, with this role and this memberStatus."
//
// The indexes (database shortcuts) make it fast to look up:
//   - "Is this user a member of this org?" (user + tenant + memberStatus)
//   - "Which orgs does this user belong to?" (user + memberStatus)
@Entity
@Table(indexes = {
        @Index(name = "idx_tenant_membership_user_tenant_status", columnList = "user_id, tenant_id, status"),
        @Index(name = "idx_tenant_membership_user_status", columnList = "user_id, status")
})
@Getter
@Setter
@NoArgsConstructor
public class TenantMembership {

    // Unique ID for this membership record (auto-generated).
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID tenantMembershipId;

    // Which organization this membership is for.
    private UUID tenantId;

    // Which person this membership belongs to.
    private UUID userId;

    // Is this an internal employee or an external partner?
    @Enumerated(EnumType.STRING)
    private UserType userType;

    // Is this membership currently active or has it been deactivated?
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private MemberStatus memberStatus;

    // The person's role in this organization (Owner, Admin, Manager, or Member).
    // This is what determines their permissions.
    @Enumerated(EnumType.STRING)
    private Roles role;

}
