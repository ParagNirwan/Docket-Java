package com.docket.app.service;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.LegalHoldDTO;
import com.docket.app.dto.LegalHoldRequestDTO;
import com.docket.app.dto.LegalHoldTargetsDTO;
import com.docket.app.dto.SearchResultDTO;
import com.docket.app.entity.LegalHold;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentSearchRepository;
import com.docket.app.repository.LegalHoldRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Keeps documents for litigation, whatever their retention dates say. documents.legal_hold
// is what RetentionGuard and the database trigger read. It's derived from the holds here,
// so applying and releasing take turns: a release running beside an apply could otherwise
// clear the flag on a document the new hold has just covered.
@Service
@RequiredArgsConstructor
public class LegalHoldService {
    private static final int SEARCH_PAGE_SIZE = 200;
    private static final int MAX_SEARCH_DOCUMENTS = 10_000;

    private final LegalHoldRepository legalHoldRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DocumentSearchRepository documentSearchRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public List<LegalHoldDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);
        return legalHoldRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream().map(this::toDTO).toList();
    }

    public Page<DocumentResponseDTO> listDocuments(String slug, UUID holdId, int page, int size, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);
        LegalHold hold = findHold(holdId, tenant.getId());
        return legalHoldRepository.findDocuments(hold.getId(), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)))
                .map(documentService::toDTO);
    }

    @Transactional
    public LegalHoldDTO create(String slug, LegalHoldRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);
        User actor = currentUser(authentication);
        takeTurn(tenant.getId());

        LegalHold hold = new LegalHold();
        hold.setTenantId(tenant.getId());
        hold.setName(request.name().trim());
        hold.setReason(request.reason().trim());
        hold.setCreatedBy(actor.getId());
        hold.setCreatedAt(Instant.now());
        hold = legalHoldRepository.saveAndFlush(hold);

        int added = addTargets(hold, request.targets());
        if (added == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No documents matched");
        }
        legalHoldRepository.markDocumentsHeld(hold.getId());

        auditService.record(tenant.getId(), actor.getId(), AuditAction.LEGAL_HOLD_APPLIED, "LEGAL_HOLD", hold.getId(),
                Map.of("name", hold.getName(), "reason", hold.getReason(), "documents", added));
        notifyHolders(tenant, NotificationType.LEGAL_HOLD_APPLIED, "Legal hold applied: " + hold.getName(),
                added + (added == 1 ? " document" : " documents") + " in " + tenant.getName() + " can't be deleted until it's released.");
        return toDTO(hold);
    }

    @Transactional
    public LegalHoldDTO addDocuments(String slug, UUID holdId, LegalHoldTargetsDTO targets, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);
        User actor = currentUser(authentication);
        takeTurn(tenant.getId());

        LegalHold hold = findHold(holdId, tenant.getId());
        if (hold.getReleasedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This legal hold has been released");
        }
        int added = addTargets(hold, targets);
        legalHoldRepository.markDocumentsHeld(hold.getId());
        auditService.record(tenant.getId(), actor.getId(), AuditAction.LEGAL_HOLD_APPLIED, "LEGAL_HOLD", hold.getId(),
                Map.of("name", hold.getName(), "documents", added));
        return toDTO(hold);
    }

    @Transactional
    public LegalHoldDTO release(String slug, UUID holdId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_OVERRIDE);
        User actor = currentUser(authentication);
        takeTurn(tenant.getId());

        LegalHold hold = findHold(holdId, tenant.getId());
        if (hold.getReleasedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This legal hold has already been released");
        }
        hold.setReleasedAt(Instant.now());
        hold.setReleasedBy(actor.getId());
        legalHoldRepository.saveAndFlush(hold);
        int affected = legalHoldRepository.refreshDocumentsHeld(hold.getId());

        auditService.record(tenant.getId(), actor.getId(), AuditAction.LEGAL_HOLD_RELEASED, "LEGAL_HOLD", hold.getId(),
                Map.of("name", hold.getName(), "documents", affected));
        notifyHolders(tenant, NotificationType.LEGAL_HOLD_RELEASED, "Legal hold released: " + hold.getName(), null);
        return toDTO(hold);
    }

    private int addTargets(LegalHold hold, LegalHoldTargetsDTO targets) {
        if (targets == null) {
            return 0;
        }
        UUID tenantId = hold.getTenantId();
        Instant now = Instant.now();
        int added = 0;
        if (targets.documentIds() != null && !targets.documentIds().isEmpty()) {
            added += legalHoldRepository.addDocuments(hold.getId(), tenantId, targets.documentIds(), now);
        }
        if (targets.folderIds() != null) {
            for (UUID folderId : targets.folderIds()) {
                added += legalHoldRepository.addFolder(hold.getId(), tenantId, folderId, now);
            }
        }
        if (targets.searchQuery() != null && !targets.searchQuery().isBlank()) {
            List<UUID> matches = searchMatches(tenantId, targets.searchQuery().trim());
            if (!matches.isEmpty()) {
                added += legalHoldRepository.addDocuments(hold.getId(), tenantId, matches, now);
            }
        }
        return added;
    }

    private List<UUID> searchMatches(UUID tenantId, String query) {
        DocumentSearchRepository.Criteria criteria =
                new DocumentSearchRepository.Criteria(tenantId, query, true, null, null, null, null);
        Page<SearchResultDTO> first = documentSearchRepository.search(criteria, 0, SEARCH_PAGE_SIZE);
        if (first.getTotalElements() > MAX_SEARCH_DOCUMENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That search matches too many documents. Narrow it down");
        }
        List<UUID> ids = new ArrayList<>(first.map(SearchResultDTO::documentId).getContent());
        for (int page = 1; page < first.getTotalPages(); page++) {
            documentSearchRepository.search(criteria, page, SEARCH_PAGE_SIZE).forEach(result -> ids.add(result.documentId()));
        }
        return ids;
    }

    private void notifyHolders(Tenant tenant, NotificationType type, String title, String body) {
        notificationService.notifyPermissionHolders(tenant.getId(), Permission.RETENTION_READ, type, title, body,
                NotificationService.workspaceLink(tenant.getSlug(), "settings", "legal-holds"));
    }

    private void takeTurn(UUID tenantId) {
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, "legal-hold:" + tenantId);
    }

    private LegalHoldDTO toDTO(LegalHold hold) {
        String createdByName = hold.getCreatedBy() == null ? null
                : userRepository.findById(hold.getCreatedBy()).map(User::getName).orElse(null);
        return new LegalHoldDTO(hold.getId(), hold.getName(), hold.getReason(), createdByName, hold.getCreatedAt(),
                hold.getReleasedAt(), legalHoldRepository.countDocuments(hold.getId()));
    }

    private LegalHold findHold(UUID holdId, UUID tenantId) {
        return legalHoldRepository.findByIdAndTenantId(holdId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal hold not found"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
