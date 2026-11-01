package com.docket.app.service;

import com.docket.app.dto.NotificationDTO;
import com.docket.app.dto.NotificationPreferencesDTO;
import com.docket.app.entity.Notification;
import com.docket.app.entity.NotificationPreference;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.NotificationPreferenceRepository;
import com.docket.app.repository.NotificationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.RolePermissions;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Tells someone something happened: under the bell, by email, or in their daily digest,
// as they choose. Called from inside a transaction, it joins it, so nobody is told about
// something that then rolls back.
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int DIGEST_LINES = 10;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final TenantMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmailOutboxService emailOutboxService;

    @Value("${docket.app.base-url}")
    private String baseUrl;

    // The in-app link to a part of a workspace. Emails get the same path on the public URL.
    public static String workspaceLink(String slug, String section, String tab) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/workspace/{slug}").queryParam("section", section);
        if (tab != null) {
            builder.queryParam("tab", tab);
        }
        return builder.buildAndExpand(slug).encode().toUriString();
    }

    @Transactional
    public void notify(UUID tenantId, UUID userId, NotificationType type, String title, String body, String link) {
        NotificationPreference preference = preferencesOf(userId);
        save(tenantId, userId, type, title, body, link, preference);
        if (preference.isEmail() && !preference.isDailyDigest()) {
            String message = body == null ? title : title + ". " + body;
            sendEmail(userId, EmailTemplate.NOTIFICATION, title, Map.of("message", message, "link", absolute(link)));
        }
    }

    // For when an email has already gone out about this, such as an invite.
    @Transactional
    public void notifyInAppOnly(UUID tenantId, UUID userId, NotificationType type, String title, String body, String link) {
        NotificationPreference preference = preferencesOf(userId);
        if (preference.isInApp()) {
            save(tenantId, userId, type, title, body, link, preference);
        }
    }

    @Transactional
    public void notifyPermissionHolders(UUID tenantId, Permission permission, NotificationType type,
                                        String title, String body, String link) {
        for (UUID userId : holdersOf(tenantId, permission)) {
            notify(tenantId, userId, type, title, body, link);
        }
    }

    // The same, with its own email template rather than the general one.
    @Transactional
    public void notifyPermissionHolders(UUID tenantId, Permission permission, NotificationType type, String title, String body,
                                        String link, EmailTemplate template, Map<String, Object> templateVariables) {
        for (UUID userId : holdersOf(tenantId, permission)) {
            NotificationPreference preference = preferencesOf(userId);
            save(tenantId, userId, type, title, body, link, preference);
            if (preference.isEmail() && !preference.isDailyDigest()) {
                Map<String, Object> variables = new HashMap<>(templateVariables);
                variables.put("link", absolute(link));
                sendEmail(userId, template, title, variables);
            }
        }
    }

    public Page<NotificationDTO> list(int page, int size, Authentication authentication) {
        User user = currentUser(authentication);
        return notificationRepository
                .findByUserIdAndInAppIsTrue(user.getId(), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::toDTO);
    }

    public long unreadCount(Authentication authentication) {
        return notificationRepository.countByUserIdAndInAppIsTrueAndReadAtIsNull(currentUser(authentication).getId());
    }

    @Transactional
    public void markRead(UUID notificationId, Authentication authentication) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser(authentication).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(Authentication authentication) {
        notificationRepository.markAllRead(currentUser(authentication).getId(), Instant.now());
    }

    public NotificationPreferencesDTO getPreferences(Authentication authentication) {
        NotificationPreference preference = preferencesOf(currentUser(authentication).getId());
        return new NotificationPreferencesDTO(preference.isInApp(), preference.isEmail(), preference.isDailyDigest());
    }

    @Transactional
    public NotificationPreferencesDTO updatePreferences(NotificationPreferencesDTO request, Authentication authentication) {
        NotificationPreference preference = preferencesOf(currentUser(authentication).getId());
        preference.setInApp(request.inApp());
        preference.setEmail(request.email());
        preference.setDailyDigest(request.dailyDigest());
        preferenceRepository.save(preference);
        return request;
    }

    // Everything waiting for one person, as one email. If they've turned the digest off
    // since, what's waiting still goes out once, rather than being lost.
    @Transactional
    public void sendDigest(UUID userId) {
        List<Notification> pending = notificationRepository.findByUserIdAndDigestPendingIsTrueOrderByCreatedAtDesc(userId);
        notificationRepository.clearPendingDigest(userId);
        if (pending.isEmpty()) {
            return;
        }
        List<String> lines = pending.stream().limit(DIGEST_LINES).map(Notification::getTitle).toList();
        String message = String.join("; ", lines);
        if (pending.size() > DIGEST_LINES) {
            message += "; and " + (pending.size() - DIGEST_LINES) + " more";
        }
        sendEmail(userId, EmailTemplate.NOTIFICATION, pending.size() + " new notifications from Docket",
                Map.of("message", message + ".", "link", absolute("/tenants")));
    }

    private void save(UUID tenantId, UUID userId, NotificationType type, String title, String body, String link,
                      NotificationPreference preference) {
        if (!preference.isInApp() && !preference.isDailyDigest()) {
            return;
        }
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setInApp(preference.isInApp());
        notification.setDigestPending(preference.isDailyDigest());
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);
    }

    private List<UUID> holdersOf(UUID tenantId, Permission permission) {
        return membershipRepository.findByTenantIdAndMemberStatus(tenantId, MemberStatus.ACTIVE).stream()
                .filter(membership -> RolePermissions.allows(membership.getRole(), permission))
                .map(TenantMembership::getUserId)
                .toList();
    }

    private void sendEmail(UUID userId, EmailTemplate template, String subject, Map<String, Object> variables) {
        userRepository.findById(userId).ifPresent(user ->
                emailOutboxService.enqueue(user.getEmail(), template, subject, variables));
    }

    private String absolute(String link) {
        return baseUrl + (link == null ? "/tenants" : link);
    }

    private NotificationPreference preferencesOf(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            NotificationPreference preference = new NotificationPreference();
            preference.setUserId(userId);
            return preference;
        });
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private NotificationDTO toDTO(Notification notification) {
        return new NotificationDTO(notification.getId(), notification.getType(), notification.getTitle(), notification.getBody(),
                notification.getLink(), notification.getReadAt() != null, notification.getCreatedAt());
    }
}
