package com.docket.app.service;

import com.docket.app.dto.ExpiringDocumentDTO;
import com.docket.app.dto.RetentionPolicyDTO;
import com.docket.app.dto.RetentionPolicyRequestDTO;
import com.docket.app.dto.RetentionSummaryDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.RetentionPolicy;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.RetentionPolicyRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Settings for how long documents are kept. A Compliance policy is a promise to a
// regulator, so it can only get stricter: anything that would let a document go sooner
// is refused, and that includes narrowing what it covers or how its clock runs.
@Service
@RequiredArgsConstructor
public class RetentionPolicyService {
    private static final Duration EXPIRING_SOON = Duration.ofDays(30);
    private static final int EXPIRING_LIST_SIZE = 20;

    private final RetentionPolicyRepository policyRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final RetentionService retentionService;
    private final AuditService auditService;

    public List<RetentionPolicyDTO> list(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);

        List<RetentionPolicy> policies = policyRepository.findByTenantId(tenant.getId());
        if (policies.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> folderNames = folderRepository.findAllById(policies.stream().map(RetentionPolicy::getFolderId).toList())
                .stream().collect(Collectors.toMap(Folder::getId, Folder::getName));
        Map<UUID, Long> lockedCounts = documentRepository.countLockedByPolicy(tenant.getId(), Instant.now()).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return policies.stream()
                .map(policy -> toDTO(policy, folderNames.get(policy.getFolderId()), lockedCounts.getOrDefault(policy.getId(), 0L)))
                .toList();
    }

    public RetentionPolicyDTO get(String slug, UUID policyId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);
        return describe(tenant.getId(), findPolicy(policyId, tenant.getId()));
    }

    public RetentionSummaryDTO summary(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);

        Instant now = Instant.now();
        Instant horizon = now.plus(EXPIRING_SOON);
        List<ExpiringDocumentDTO> expiring = documentRepository
                .findExpiringBetween(tenant.getId(), now, horizon, PageRequest.of(0, EXPIRING_LIST_SIZE)).stream()
                .map(document -> new ExpiringDocumentDTO(document.getId(), document.getOriginalFileName(),
                        document.getFolderId(), document.getRetainUntil(), document.getLockMode()))
                .toList();
        return new RetentionSummaryDTO(
                documentRepository.countLocked(tenant.getId(), now),
                documentRepository.countExpiringBetween(tenant.getId(), now, horizon),
                expiring,
                tenant.getTrashRetentionDays());
    }

    @Transactional
    public RetentionPolicyDTO create(String slug, RetentionPolicyRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);

        Folder folder = folderRepository.findById(request.folderId())
                .filter(found -> tenant.getId().equals(found.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (Boolean.TRUE.equals(folder.getIsDefault()) && TrashService.TRASH_TRAY.equals(folder.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Trash can't have a retention policy");
        }
        if (policyRepository.existsByFolderId(folder.getId())) {
            throw alreadyHasPolicy();
        }

        Instant now = Instant.now();
        RetentionPolicy policy = new RetentionPolicy();
        policy.setTenantId(tenant.getId());
        policy.setFolderId(folder.getId());
        policy.setCreatedBy(userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId());
        policy.setCreatedAt(now);
        copyTerms(request, policy, now);
        try {
            policy = policyRepository.saveAndFlush(policy);
        } catch (DataIntegrityViolationException e) {
            throw alreadyHasPolicy();
        }

        int applied = retentionService.applyPolicy(policy);
        auditService.record(authentication, tenant.getId(), AuditAction.RETENTION_POLICY_CREATED, "RETENTION_POLICY", policy.getId(),
                Map.of("folderId", folder.getId().toString(), "folder", folder.getName(), "retentionDays", policy.getRetentionDays(),
                        "lockMode", policy.getLockMode().name(), "documents", applied));
        return describe(tenant.getId(), policy);
    }

    @Transactional
    public RetentionPolicyDTO update(String slug, UUID policyId, RetentionPolicyRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);

        RetentionPolicy policy = findPolicy(policyId, tenant.getId());
        if (!policy.getFolderId().equals(request.folderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A policy can't be moved to another folder");
        }
        if (policy.getLockMode() == LockMode.COMPLIANCE) {
            requireNotWeakened(policy, request);
        }

        int previousDays = policy.getRetentionDays();
        LockMode previousMode = policy.getLockMode();
        copyTerms(request, policy, Instant.now());
        policy = policyRepository.saveAndFlush(policy);

        int applied = retentionService.applyPolicy(policy);
        auditService.record(authentication, tenant.getId(), AuditAction.RETENTION_POLICY_UPDATED, "RETENTION_POLICY", policy.getId(),
                Map.of("fromDays", previousDays, "toDays", policy.getRetentionDays(),
                        "fromMode", previousMode.name(), "toMode", policy.getLockMode().name(), "documents", applied));
        return describe(tenant.getId(), policy);
    }

    // Documents already locked stay locked until their dates: removing a policy only
    // stops it applying to new ones.
    @Transactional
    public void delete(String slug, UUID policyId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);

        RetentionPolicy policy = findPolicy(policyId, tenant.getId());
        if (policy.getLockMode() == LockMode.COMPLIANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Compliance policy can't be removed");
        }
        policyRepository.delete(policy);
        auditService.record(authentication, tenant.getId(), AuditAction.RETENTION_POLICY_DELETED, "RETENTION_POLICY", policyId,
                Map.of("folderId", policy.getFolderId().toString(), "retentionDays", policy.getRetentionDays()));
    }

    private void requireNotWeakened(RetentionPolicy policy, RetentionPolicyRequestDTO request) {
        if (request.lockMode() != LockMode.COMPLIANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Compliance policy can't become Governance");
        }
        if (request.retentionDays() < policy.getRetentionDays()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Compliance policy can only be extended, not shortened");
        }
        if (request.clockStart() != policy.getClockStart()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Compliance policy's start date can't change");
        }
        if (policy.getAppliesToSubfolders() && !request.appliesToSubfolders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Compliance policy can't stop covering sub-folders");
        }
    }

    private void copyTerms(RetentionPolicyRequestDTO request, RetentionPolicy policy, Instant now) {
        policy.setRetentionDays(request.retentionDays());
        policy.setLockMode(request.lockMode());
        policy.setClockStart(request.clockStart());
        policy.setAppliesToSubfolders(request.appliesToSubfolders());
        policy.setOnExpiry(request.onExpiry());
        policy.setUpdatedAt(now);
    }

    private RetentionPolicyDTO describe(UUID tenantId, RetentionPolicy policy) {
        String folderName = folderRepository.findById(policy.getFolderId()).map(Folder::getName).orElse(null);
        return toDTO(policy, folderName, documentRepository.countLockedForPolicy(tenantId, policy.getId(), Instant.now()));
    }

    private RetentionPolicyDTO toDTO(RetentionPolicy policy, String folderName, long lockedDocuments) {
        return new RetentionPolicyDTO(
                policy.getId(),
                policy.getFolderId(),
                folderName,
                policy.getRetentionDays(),
                policy.getLockMode(),
                policy.getClockStart(),
                policy.getAppliesToSubfolders(),
                policy.getOnExpiry(),
                lockedDocuments,
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }

    private RetentionPolicy findPolicy(UUID policyId, UUID tenantId) {
        return policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Retention policy not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private ResponseStatusException alreadyHasPolicy() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This folder already has a retention policy");
    }
}
