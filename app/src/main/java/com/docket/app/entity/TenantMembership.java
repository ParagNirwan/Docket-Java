package com.docket.app.entity;

import com.docket.app.enums.Roles;
import com.docket.app.enums.UserType;
import com.docket.app.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TenantMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID tenantMembershipId;
    private UUID tenantId;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private UserType userType;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Enumerated(EnumType.STRING)
    private Roles role;

}
