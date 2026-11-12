package com.docket.app.entity;

import com.docket.app.enums.Plan;
import com.docket.app.enums.TenantStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String slug;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private TenantStatus status;
    private Integer trashRetentionDays = 30;
    private Long storageQuotaBytes;
    // Whether the AI may write a summary of each document. See DocumentSummaryService.
    private boolean aiSummariesEnabled = true;

    // Whether the AI may work out each document's type, tags and key fields. See DocumentInsightsService.
    private boolean aiClassificationEnabled = true;

    // Apply the tags the AI suggests without waiting for someone to confirm them
    private boolean aiAutoApplyTags = false;

    // Admins can switch shareable invite links off for the whole organization
    private boolean inviteLinksEnabled = true;

    @Enumerated(EnumType.STRING)
    private Plan plan = Plan.FREE;

    // Billing details are for the Owner's billing page only, never the general tenant response
    @JsonIgnore
    private String stripeCustomerId;

    @JsonIgnore
    private String stripeSubscriptionId;

    @JsonIgnore
    private String subscriptionStatus;

    @JsonIgnore
    private Instant planRenewsAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
