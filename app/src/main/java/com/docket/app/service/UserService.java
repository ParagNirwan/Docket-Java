package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.Status;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Handles user-related operations — currently just listing which
// organizations a user belongs to.
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;

    // "Which organizations does this person belong to?"
    // This powers the "All tenants" page where users see their org list.
    public List<Tenant> listTenantsForUser(String email) {

        // Look up the user by email.
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Find all their active memberships. If they were removed from an org,
        // that membership is no longer active, so it won't show up here.
        List<TenantMembership> memberships = tenantMembershipRepository.findByUserIdAndStatus(
                user.getId(),
                Status.ACTIVE
        );

        // Get the organization IDs from those memberships.
        List<UUID> tenantIds = memberships.stream()
                .map(TenantMembership::getTenantId)
                .collect(Collectors.toList());

        // Fetch the actual organizations, but only return the ones that are
        // still active. If an org was shut down, don't show it even if the
        // person is technically still a member.
        return tenantRepository.findAllById(tenantIds).stream()
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .toList();
    }
}