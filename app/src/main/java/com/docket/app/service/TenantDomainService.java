package com.docket.app.service;

import com.docket.app.dto.TenantDomainDTO;
import com.docket.app.dto.UpdateTenantDomainRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantDomain;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.DomainJoinMode;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.TenantStatus;
import com.docket.app.enums.UserType;
import com.docket.app.repository.TenantDomainRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantDomainService {
    private final TenantDomainRepository tenantDomainRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public List<TenantDomainDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);
        return tenantDomainRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TenantDomainDTO add(String slug, String domain, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        String normalized = normalizeDomain(domain);
        if (tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This domain is already configured");
        }

        TenantDomain tenantDomain = new TenantDomain();
        tenantDomain.setTenantId(tenant.getId());
        tenantDomain.setDomain(normalized);
        tenantDomain.setJoinMode(DomainJoinMode.REQUEST);
        tenantDomain.setVerificationToken(randomToken());
        tenantDomain.setSsoRequired(false);
        tenantDomainRepository.save(tenantDomain);

        auditService.record(authentication, tenant.getId(), AuditAction.DOMAIN_ADDED, "TENANT_DOMAIN", tenantDomain.getId(),
                Map.of("domain", normalized));
        return toDto(tenantDomain);
    }

    @Transactional
    public TenantDomainDTO verify(String slug, String domain, String token, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        String normalized = normalizeDomain(domain);
        TenantDomain tenantDomain = tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));

        if (!token.equals(tenantDomain.getVerificationToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is invalid");
        }

        tenantDomain.setVerifiedAt(Instant.now());
        tenantDomain.setVerificationToken(randomToken());
        tenantDomainRepository.save(tenantDomain);

        auditService.record(authentication, tenant.getId(), AuditAction.DOMAIN_VERIFIED, "TENANT_DOMAIN", tenantDomain.getId(),
                Map.of("domain", normalized));
        return toDto(tenantDomain);
    }

    @Transactional
    public TenantDomainDTO update(String slug, String domain, UpdateTenantDomainRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        String normalized = normalizeDomain(domain);
        TenantDomain tenantDomain = tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));

        if (request.joinMode() != null) {
            tenantDomain.setJoinMode(request.joinMode());
        }
        if (request.ssoRequired() != null) {
            tenantDomain.setSsoRequired(request.ssoRequired());
        }
        tenantDomainRepository.save(tenantDomain);

        auditService.record(authentication, tenant.getId(), AuditAction.DOMAIN_UPDATED, "TENANT_DOMAIN", tenantDomain.getId(),
                Map.of("domain", normalized, "joinMode", String.valueOf(tenantDomain.getJoinMode()),
                        "ssoRequired", Boolean.toString(tenantDomain.isSsoRequired())));
        return toDto(tenantDomain);
    }

    @Transactional
    public void remove(String slug, String domain, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);

        String normalized = normalizeDomain(domain);
        TenantDomain tenantDomain = tenantDomainRepository.findByTenantIdAndDomainIgnoreCase(tenant.getId(), normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));

        tenantDomainRepository.delete(tenantDomain);
        auditService.record(authentication, tenant.getId(), AuditAction.DOMAIN_REMOVED, "TENANT_DOMAIN", tenantDomain.getId(),
                Map.of("domain", normalized));
    }

    @Transactional
    public boolean autoJoinVerifiedDomain(User user) {
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isBlank()) {
            return false;
        }

        String domain = extractDomain(email);
        if (domain == null) {
            return false;
        }

        Optional<TenantDomain> match = tenantDomainRepository.findByDomainIgnoreCaseAndVerifiedAtIsNotNull(domain)
                .stream()
                .filter(tenantDomain -> !tenantDomain.isSsoRequired())
                .filter(tenantDomain -> tenantDomain.getJoinMode() == DomainJoinMode.AUTO)
                .findFirst();

        if (match.isEmpty()) {
            return false;
        }

        TenantDomain tenantDomain = match.get();
        Tenant tenant = tenantRepository.findById(tenantDomain.getTenantId())
                .orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
            return false;
        }

        boolean alreadyMember = membershipRepository.findByUserIdAndTenantIdAndMemberStatus(
                user.getId(), tenant.getId(), MemberStatus.ACTIVE
        ).size() > 0;
        if (alreadyMember) {
            return false;
        }

        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenant.getId());
        membership.setUserId(user.getId());
        membership.setUserType(UserType.INTERNAL);
        membership.setMemberStatus(MemberStatus.ACTIVE);
        membership.setRole(Roles.MEMBER);
        membership.setJoinedAt(Instant.now());
        membershipRepository.save(membership);

        auditService.record(tenant.getId(), user.getId(), AuditAction.MEMBER_JOINED_BY_DOMAIN, "TENANT", tenant.getId(),
                Map.of("domain", domain, "role", Roles.MEMBER.name()));
        return true;
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private static String normalizeDomain(String domain) {
        String normalized = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            normalized = normalized.replaceFirst("^https?://", "");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String extractDomain(String email) {
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private TenantDomainDTO toDto(TenantDomain tenantDomain) {
        return new TenantDomainDTO(
                tenantDomain.getDomain(),
                tenantDomain.getVerificationToken(),
                tenantDomain.getVerifiedAt(),
                tenantDomain.getJoinMode(),
                tenantDomain.isSsoRequired(),
                tenantDomain.getCreatedAt()
        );
    }
}
