package com.docket.app.service;

import com.docket.app.dto.EmailVerificationSettingsDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailVerificationSettingsService {
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AccountRecoveryService accountRecoveryService;
    private final AuditService auditService;

    @Value("${docket.email.verification.enabled:false}")
    private boolean globallyEnabled;

    public EmailVerificationSettingsDTO get(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_READ);
        return new EmailVerificationSettingsDTO(tenant.isEmailVerificationEnabled());
    }

    @Transactional
    public EmailVerificationSettingsDTO update(
            String slug,
            EmailVerificationSettingsDTO settings,
            Authentication authentication
    ) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        boolean changed = tenant.isEmailVerificationEnabled() != settings.enabled();
        tenant.setEmailVerificationEnabled(settings.enabled());
        tenantRepository.save(tenant);

        if (changed) {
            auditService.record(authentication, tenant.getId(), AuditAction.EMAIL_VERIFICATION_SETTINGS_UPDATED,
                    "TENANT", tenant.getId(), Map.of("enabled", Boolean.toString(settings.enabled())));
            if (settings.enabled()) {
                sendToUnverifiedMembers(tenant.getId());
            }
        }
        return new EmailVerificationSettingsDTO(tenant.isEmailVerificationEnabled());
    }

    public boolean isEnabledForUser(String email) {
        if (globallyEnabled) {
            return true;
        }
        return userRepository.findByEmailIgnoreCase(email)
                .map(User::getId)
                .map(this::isEnabledForUser)
                .orElse(false);
    }

    public boolean isEnabledForUser(UUID userId) {
        if (globallyEnabled) {
            return true;
        }
        Set<UUID> tenantIds = tenantMembershipRepository
                .findByUserIdAndMemberStatus(userId, MemberStatus.ACTIVE)
                .stream()
                .map(membership -> membership.getTenantId())
                .collect(Collectors.toSet());
        return tenantRepository.findAllById(tenantIds).stream()
                .anyMatch(Tenant::isEmailVerificationEnabled);
    }

    private void sendToUnverifiedMembers(UUID tenantId) {
        Set<UUID> userIds = tenantMembershipRepository.findByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE)
                .stream()
                .map(membership -> membership.getUserId())
                .collect(Collectors.toSet());
        userRepository.findAllById(userIds).stream()
                .filter(user -> user.getEmailVerifiedAt() == null)
                .forEach(accountRecoveryService::sendVerificationEmail);
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
