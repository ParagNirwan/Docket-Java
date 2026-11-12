package com.docket.app.service;

import com.docket.app.dto.BulkInviteRequestDTO;
import com.docket.app.dto.BulkInviteResultDTO;
import com.docket.app.dto.BulkInviteRowDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantMembership;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.InvitationStatus;
import com.docket.app.enums.MemberStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.repository.TenantInvitationRepository;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// Invites many people from a CSV file of "email,role,name". The file is first turned into a
// preview that says what would happen to each line, and only the lines the person confirms
// are sent. Every line is checked again when sending, because the preview is only advice.
@Service
@RequiredArgsConstructor
public class BulkInviteService {
    private static final Logger log = LoggerFactory.getLogger(BulkInviteService.class);
    static final int MAX_ROWS = 200;
    private static final int MAX_CSV_LENGTH = 100_000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final InvitationService invitationService;
    private final AuthRateLimiter authRateLimiter;
    private final AuditService auditService;

    public List<BulkInviteRowDTO> preview(String slug, String csv, Authentication authentication) {
        Tenant tenant = requireInviter(slug, authentication);
        if (csv == null || csv.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (csv.length() > MAX_CSV_LENGTH) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large");
        }
        List<BulkInviteRequestDTO.Row> rows = parse(csv);
        if (rows.size() > MAX_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_ROWS + " people can be invited at once");
        }
        return check(tenant, rows, authentication);
    }

    // Not one transaction on purpose: each invite commits by itself, so a line that fails
    // doesn't undo the ones before it. The emails go out through the outbox afterwards.
    public BulkInviteResultDTO send(String slug, BulkInviteRequestDTO request, Authentication authentication) {
        Tenant tenant = requireInviter(slug, authentication);
        authRateLimiter.check("invite-bulk:tenant:" + tenant.getId(), 5, Duration.ofHours(1));
        User inviter = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        List<BulkInviteRowDTO> results = new ArrayList<>();
        int sent = 0;
        int errors = 0;
        for (BulkInviteRowDTO row : check(tenant, request.rows(), authentication)) {
            if (!"OK".equals(row.status())) {
                results.add(row);
                continue;
            }
            try {
                invitationService.createInvitation(tenant, inviter, row.email(), Roles.valueOf(row.role()));
                results.add(withStatus(row, "SENT", "Invitation sent"));
                sent++;
            } catch (ResponseStatusException e) {
                results.add(withStatus(row, "ERROR", e.getReason()));
                errors++;
            } catch (RuntimeException e) {
                log.warn("Bulk invite failed for one row in tenant {}", tenant.getId(), e);
                results.add(withStatus(row, "ERROR", "Could not send this invitation"));
                errors++;
            }
        }
        int skipped = results.size() - sent - errors;
        auditService.record(tenant.getId(), inviter.getId(), AuditAction.MEMBERS_BULK_INVITED, "TENANT", tenant.getId(),
                Map.of("sent", sent, "skipped", skipped, "errors", errors));
        return new BulkInviteResultDTO(sent, skipped, errors, results);
    }

    private Tenant requireInviter(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.MEMBERS_INVITE);
        return tenant;
    }

    private List<BulkInviteRowDTO> check(Tenant tenant, List<BulkInviteRequestDTO.Row> rows, Authentication authentication) {
        UUID tenantId = tenant.getId();
        Roles actorRole = tenantAuthorizationService.getMembershipRole(authentication, tenantId);
        Instant now = Instant.now();
        Set<String> seen = new HashSet<>();
        List<BulkInviteRowDTO> checked = new ArrayList<>();
        int line = 0;
        for (BulkInviteRequestDTO.Row row : rows) {
            line++;
            String email = row.email() == null ? "" : row.email().trim().toLowerCase(Locale.ROOT);
            String roleText = row.role() == null || row.role().isBlank() ? "MEMBER" : row.role().trim().toUpperCase(Locale.ROOT);
            String name = row.name() == null ? "" : row.name().trim();
            Roles role = parseRole(roleText);

            String status = "OK";
            String message = "Ready to invite";
            if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
                status = "INVALID_EMAIL";
                message = "This isn't a valid email address";
            } else if (role == null) {
                status = "INVALID_ROLE";
                message = "Unknown role. Use ADMIN, MANAGER, MEMBER or GUEST";
            } else if (!RolePermissions.canAssignRole(actorRole, role)) {
                status = "ROLE_NOT_ALLOWED";
                message = "You can't give out this role";
            } else if (!seen.add(email)) {
                status = "DUPLICATE";
                message = "This address appears more than once";
            } else if (isMember(tenantId, email)) {
                status = "ALREADY_MEMBER";
                message = "Already a member";
            } else if (invitationRepository.findByTenantIdAndEmailIgnoreCaseAndStatus(tenantId, email, InvitationStatus.PENDING)
                    .filter(invitation -> invitation.getExpiresAt().isAfter(now)).isPresent()) {
                status = "ALREADY_INVITED";
                message = "Already has a pending invitation";
            }
            checked.add(new BulkInviteRowDTO(line, email, roleText, name, status, message));
        }
        return checked;
    }

    private boolean isMember(UUID tenantId, String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> membershipRepository.findByUserIdAndTenantIdAndMemberStatus(user.getId(), tenantId, MemberStatus.ACTIVE))
                .map(memberships -> !memberships.isEmpty())
                .orElse(false);
    }

    private static Roles parseRole(String text) {
        try {
            return Roles.valueOf(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BulkInviteRowDTO withStatus(BulkInviteRowDTO row, String status, String message) {
        return new BulkInviteRowDTO(row.line(), row.email(), row.role(), row.name(), status, message);
    }

    // A header line is optional. Fields may be quoted, with "" for a quote inside one.
    static List<BulkInviteRequestDTO.Row> parse(String csv) {
        List<BulkInviteRequestDTO.Row> rows = new ArrayList<>();
        for (String line : csv.replace("﻿", "").split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = splitLine(line);
            String first = fields.getFirst().trim();
            if (rows.isEmpty() && first.equalsIgnoreCase("email")) {
                continue;
            }
            rows.add(new BulkInviteRequestDTO.Row(
                    first,
                    fields.size() > 1 ? fields.get(1).trim() : "",
                    fields.size() > 2 ? fields.get(2).trim() : ""));
        }
        return rows;
    }

    private static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',' || c == ';') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
