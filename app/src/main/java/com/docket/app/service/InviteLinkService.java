package com.docket.app.service;

import com.docket.app.dto.AcceptedInvitationDTO;
import com.docket.app.dto.CreateInviteLinkRequestDTO;
import com.docket.app.dto.InviteLinkDTO;
import com.docket.app.dto.InviteLinkPreviewDTO;
import com.docket.app.dto.InviteLinkSettingsDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantInviteLink;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantInviteLinkRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Shareable links that let anyone who has one join with a set role. Unlike an email invite
// a link isn't tied to an address, so it can carry an expiry and a number of uses, and an
// Admin can switch links off for the whole organization.
@Service
@RequiredArgsConstructor
public class InviteLinkService {
    private final TenantInviteLinkRepository linkRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final InvitationService invitationService;
    private final AuditService auditService;

    @Value("${docket.app.base-url}")
    private String baseUrl;

    // The same rules as an email invite: you can only hand out roles you could assign yourself.
    @Transactional
    public InviteLinkDTO create(String slug, CreateInviteLinkRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_INVITE);
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), request.role());
        if (!tenant.isInviteLinksEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite links are switched off for this organization");
        }

        User creator = currentUser(authentication);
        Instant now = Instant.now();
        String rawToken = TokenHasher.newToken();
        TenantInviteLink link = new TenantInviteLink();
        link.setTenantId(tenant.getId());
        link.setTokenHash(TokenHasher.hash(rawToken));
        link.setRole(request.role());
        link.setMaxUses(request.maxUses());
        link.setExpiresAt(request.expiresInDays() == null ? null : now.plus(Duration.ofDays(request.expiresInDays())));
        link.setCreatedBy(creator.getId());
        link.setCreatedAt(now);
        linkRepository.save(link);

        auditService.record(tenant.getId(), creator.getId(), AuditAction.INVITE_LINK_CREATED, "INVITE_LINK", link.getId(),
                Map.of("role", request.role().name()));
        return toDTO(link, creator.getName(), baseUrl + "/join/" + rawToken);
    }

    // Links that are used up or expired are left out: there's nothing left to manage on them.
    public List<InviteLinkDTO> list(String slug, Authentication authentication) {
        UUID tenantId = findTenant(slug).getId();
        tenantAuthorizationService.requirePermission(authentication, tenantId, Permission.MEMBERS_READ);

        Instant now = Instant.now();
        List<TenantInviteLink> links = linkRepository.findByTenantIdAndRevokedAtIsNullOrderByCreatedAtDesc(tenantId).stream()
                .filter(link -> isUsable(link, now))
                .toList();
        Map<UUID, String> creatorNames = userRepository
                .findAllById(links.stream().map(TenantInviteLink::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        return links.stream().map(link -> toDTO(link, creatorNames.get(link.getCreatedBy()), null)).toList();
    }

    @Transactional
    public void revoke(String slug, UUID linkId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_INVITE);
        TenantInviteLink link = linkRepository.findByIdAndTenantId(linkId, tenant.getId())
                .filter(found -> found.getRevokedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found"));
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), link.getRole());

        link.setRevokedAt(Instant.now());
        linkRepository.save(link);
        auditService.record(authentication, tenant.getId(), AuditAction.INVITE_LINK_REVOKED, "INVITE_LINK", linkId,
                Map.of("role", link.getRole().name()));
    }

    // Existing links stop working while this is off, and work again if it's switched back on.
    @Transactional
    public InviteLinkSettingsDTO updateSettings(String slug, InviteLinkSettingsDTO settings, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.TENANT_UPDATE);
        if (tenant.isInviteLinksEnabled() != settings.enabled()) {
            tenant.setInviteLinksEnabled(settings.enabled());
            tenantRepository.save(tenant);
            auditService.record(authentication, tenant.getId(), AuditAction.INVITE_LINK_SETTINGS_UPDATED, "TENANT",
                    tenant.getId(), Map.of("enabled", String.valueOf(settings.enabled())));
        }
        return new InviteLinkSettingsDTO(tenant.isInviteLinksEnabled());
    }

    // Public: the link itself is the credential. Every kind of dead link looks the same,
    // so nothing can be learned from guessing.
    public InviteLinkPreviewDTO preview(String rawToken) {
        TenantInviteLink link = findLive(rawToken);
        Tenant tenant = findOpenTenant(link);
        return new InviteLinkPreviewDTO(tenant.getName(), tenant.getSlug(), link.getRole());
    }

    @Transactional
    public AcceptedInvitationDTO join(String rawToken, Authentication authentication) {
        return join(rawToken, currentUser(authentication));
    }

    // Also used when someone registers from a link. Someone who is already a member doesn't
    // take one of the link's uses.
    @Transactional
    public AcceptedInvitationDTO join(String rawToken, User user) {
        TenantInviteLink link = findLive(rawToken);
        Tenant tenant = findOpenTenant(link);
        if (invitationService.activateMembership(tenant, user, link.getRole())) {
            if (linkRepository.claimUse(link.getId(), Instant.now()) == 0) {
                // Someone else took the last use a moment ago. Throwing undoes the membership.
                throw invalidLink();
            }
            auditService.record(tenant.getId(), user.getId(), AuditAction.INVITE_LINK_USED, "INVITE_LINK", link.getId(),
                    Map.of("role", link.getRole().name()));
        }
        return new AcceptedInvitationDTO(tenant.getName(), tenant.getSlug());
    }

    private TenantInviteLink findLive(String rawToken) {
        Instant now = Instant.now();
        return linkRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .filter(link -> link.getRevokedAt() == null && isUsable(link, now))
                .orElseThrow(this::invalidLink);
    }

    private static boolean isUsable(TenantInviteLink link, Instant now) {
        return (link.getExpiresAt() == null || link.getExpiresAt().isAfter(now))
                && (link.getMaxUses() == null || link.getUses() < link.getMaxUses());
    }

    private Tenant findOpenTenant(TenantInviteLink link) {
        return tenantRepository.findById(link.getTenantId())
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE && tenant.isInviteLinksEnabled())
                .orElseThrow(this::invalidLink);
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private InviteLinkDTO toDTO(TenantInviteLink link, String creatorName, String url) {
        return new InviteLinkDTO(link.getId(), link.getRole(), link.getMaxUses(), link.getUses(), link.getExpiresAt(),
                creatorName, link.getCreatedAt(), url);
    }

    private ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This invite link is invalid or has expired");
    }
}
