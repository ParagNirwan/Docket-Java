package com.docket.app.service;

import com.docket.app.dto.AcceptedInvitationDTO;
import com.docket.app.dto.InvitationDTO;
import com.docket.app.dto.InvitationPreviewDTO;
import com.docket.app.dto.InviteMemberRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantInvitation;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.InvitationStatus;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.TenantStatus;
import com.docket.app.enums.UserType;
import com.docket.app.repository.TenantInvitationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Invites people to an organization by email. Like password reset, the link proves
// someone controls the inbox: only the token's hash is stored, and only the newest
// email for an invite works.
@Service
@RequiredArgsConstructor
public class InvitationService {
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final TenantInvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuthRateLimiter authRateLimiter;
    private final EmailOutboxService emailOutboxService;
    private final AuditService auditService;

    @Value("${docket.app.base-url}")
    private String baseUrl;

    @Value("${docket.invitations.ttl-days}")
    private int ttlDays;

    @Value("${docket.invitations.limit-per-hour}")
    private int limitPerHour;

    @Transactional
    public InvitationDTO invite(String slug, InviteMemberRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        UUID tenantId = tenant.getId();
        tenantAuthorizationService.requirePermission(authentication, tenantId, Permission.MEMBERS_INVITE);
        tenantAuthorizationService.requireCanAssignRole(authentication, tenantId, request.getRole());
        authRateLimiter.check(limitKey(tenantId), limitPerHour, ONE_HOUR);

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !membershipRepository
                        .findByUserIdAndTenantIdAndMemberStatus(user.getId(), tenantId, MemberStatus.ACTIVE).isEmpty())
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is already a member");
                });
        retireExpiredOrRejectPending(tenantId, email);

        User inviter = currentUser(authentication);
        Instant now = Instant.now();
        TenantInvitation invitation = new TenantInvitation();
        invitation.setTenantId(tenantId);
        invitation.setEmail(email);
        invitation.setRole(request.getRole());
        invitation.setInvitedBy(inviter.getId());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        String rawToken = refreshToken(invitation, now);

        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            throw alreadyInvited();
        }

        sendEmail(invitation, rawToken, inviter, tenant);
        auditService.record(tenantId, inviter.getId(), AuditAction.MEMBER_INVITED, "INVITATION", invitation.getId(),
                Map.of("email", email, "role", request.getRole().name()));
        return toDTO(invitation, inviter.getName());
    }

    public List<InvitationDTO> listPending(String slug, Authentication authentication) {
        UUID tenantId = findTenant(slug).getId();
        tenantAuthorizationService.requirePermission(authentication, tenantId, Permission.MEMBERS_READ);

        List<TenantInvitation> invitations = invitationRepository
                .findByTenantIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(tenantId, InvitationStatus.PENDING, Instant.now());
        Set<UUID> inviterIds = invitations.stream()
                .map(TenantInvitation::getInvitedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> inviterNames = inviterIds.isEmpty() ? Map.of()
                : userRepository.findAllById(inviterIds).stream().collect(Collectors.toMap(User::getId, User::getName));

        return invitations.stream().map(i -> toDTO(i, inviterNames.get(i.getInvitedBy()))).toList();
    }

    // Issues a new link and restarts the clock. The link in the earlier email stops working.
    @Transactional
    public InvitationDTO resend(String slug, UUID invitationId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        TenantInvitation invitation = findManageable(tenant, invitationId, authentication);
        authRateLimiter.check(limitKey(tenant.getId()), limitPerHour, ONE_HOUR);

        User inviter = currentUser(authentication);
        String rawToken = refreshToken(invitation, Instant.now());
        invitationRepository.save(invitation);

        sendEmail(invitation, rawToken, inviter, tenant);
        auditService.record(tenant.getId(), inviter.getId(), AuditAction.INVITATION_RESENT, "INVITATION", invitation.getId(),
                Map.of("email", invitation.getEmail()));
        return toDTO(invitation, inviter.getName());
    }

    @Transactional
    public void revoke(String slug, UUID invitationId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        TenantInvitation invitation = findManageable(tenant, invitationId, authentication);

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        auditService.record(authentication, tenant.getId(), AuditAction.INVITATION_REVOKED, "INVITATION", invitation.getId(),
                Map.of("email", invitation.getEmail()));
    }

    // Public: the link itself is the credential. Invalid links of every kind look the
    // same, so nothing can be learned from guessing.
    public InvitationPreviewDTO preview(String rawToken) {
        TenantInvitation invitation = findLive(rawToken);
        Tenant tenant = findActiveTenant(invitation);
        String inviterName = invitation.getInvitedBy() == null ? null
                : userRepository.findById(invitation.getInvitedBy()).map(User::getName).orElse(null);

        return new InvitationPreviewDTO(
                tenant.getName(),
                tenant.getSlug(),
                inviterName,
                invitation.getEmail(),
                invitation.getRole(),
                userRepository.existsByEmailIgnoreCase(invitation.getEmail()));
    }

    @Transactional
    public AcceptedInvitationDTO accept(String rawToken, Authentication authentication) {
        User user = currentUser(authentication);
        TenantInvitation invitation = findLive(rawToken);
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is for a different email address");
        }
        return join(invitation, user);
    }

    // Registering from an invite link. A mismatched address isn't an error for the caller
    // to show: the account is fine, it just hasn't joined anything.
    @Transactional
    public boolean acceptForNewUser(String rawToken, User user) {
        TenantInvitation invitation = findLive(rawToken);
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            return false;
        }
        join(invitation, user);
        return true;
    }

    private AcceptedInvitationDTO join(TenantInvitation invitation, User user) {
        Tenant tenant = findActiveTenant(invitation);
        Instant now = Instant.now();
        if (invitationRepository.markAccepted(invitation.getId(), now) == 0) {
            throw invalidLink();
        }

        List<TenantMembership> memberships = membershipRepository.findByUserIdAndTenantId(user.getId(), tenant.getId());
        boolean alreadyActive = memberships.stream().anyMatch(m -> m.getMemberStatus() == MemberStatus.ACTIVE);
        if (!alreadyActive) {
            // Someone who left or was removed gets their old record back.
            TenantMembership membership = memberships.isEmpty() ? new TenantMembership() : memberships.getFirst();
            membership.setTenantId(tenant.getId());
            membership.setUserId(user.getId());
            membership.setUserType(UserType.INTERNAL);
            membership.setMemberStatus(MemberStatus.ACTIVE);
            membership.setRole(invitation.getRole());
            membership.setJoinedAt(now);
            try {
                membershipRepository.saveAndFlush(membership);
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is already a member");
            }
        }

        // The link arrived in their inbox, so the address is proven too.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }

        auditService.record(tenant.getId(), user.getId(), AuditAction.INVITATION_ACCEPTED, "INVITATION", invitation.getId(),
                Map.of("email", invitation.getEmail(), "role", invitation.getRole().name()));
        return new AcceptedInvitationDTO(tenant.getName(), tenant.getSlug());
    }

    // An expired invite still holds the "one pending invite per address" slot until the
    // nightly job clears it, and shouldn't stop anyone sending a new one.
    private void retireExpiredOrRejectPending(UUID tenantId, String email) {
        invitationRepository.findByTenantIdAndEmailIgnoreCaseAndStatus(tenantId, email, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    if (existing.getExpiresAt().isAfter(Instant.now())) {
                        throw alreadyInvited();
                    }
                    existing.setStatus(InvitationStatus.EXPIRED);
                    invitationRepository.saveAndFlush(existing);
                });
    }

    private TenantInvitation findManageable(Tenant tenant, UUID invitationId, Authentication authentication) {
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_INVITE);
        TenantInvitation invitation = invitationRepository
                .findByIdAndTenantIdAndStatus(invitationId, tenant.getId(), InvitationStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        tenantAuthorizationService.requireCanAssignRole(authentication, tenant.getId(), invitation.getRole());
        return invitation;
    }

    private TenantInvitation findLive(String rawToken) {
        return invitationRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .filter(i -> i.getStatus() == InvitationStatus.PENDING && i.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(this::invalidLink);
    }

    private Tenant findActiveTenant(TenantInvitation invitation) {
        return tenantRepository.findById(invitation.getTenantId())
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
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

    private String refreshToken(TenantInvitation invitation, Instant now) {
        String rawToken = TokenHasher.newToken();
        invitation.setTokenHash(TokenHasher.hash(rawToken));
        invitation.setExpiresAt(now.plus(Duration.ofDays(ttlDays)));
        return rawToken;
    }

    private void sendEmail(TenantInvitation invitation, String rawToken, User inviter, Tenant tenant) {
        String link = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/invite")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailOutboxService.enqueue(invitation.getEmail(), EmailTemplate.INVITE, Map.of(
                "inviterName", inviter.getName(),
                "tenantName", tenant.getName(),
                "link", link));
    }

    private InvitationDTO toDTO(TenantInvitation invitation, String inviterName) {
        return new InvitationDTO(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                inviterName,
                invitation.getCreatedAt(),
                invitation.getExpiresAt());
    }

    private static String limitKey(UUID tenantId) {
        return "invite:tenant:" + tenantId;
    }

    private ResponseStatusException alreadyInvited() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This person already has a pending invitation");
    }

    private ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This invitation is invalid or has expired");
    }
}
