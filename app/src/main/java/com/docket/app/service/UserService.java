package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
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

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantRepository tenantRepository;

    public List<Tenant> listTenantsForUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Get all tenant memberships for this user
        List<TenantMembership> memberships = tenantMembershipRepository.findByUserId(user.getId());

        // Extract tenant IDs and fetch the actual Tenant entities
        List<UUID> tenantIds = memberships.stream()
                .map(TenantMembership::getTenantId)
                .collect(Collectors.toList());

        return tenantRepository.findAllById(tenantIds);
    }
}