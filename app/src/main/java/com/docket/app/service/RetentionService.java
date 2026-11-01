package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.RetentionPolicy;
import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.RetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Works out how long documents must be kept. A retention date only ever moves later,
// and Compliance beats Governance, so the strictest policy over a document always wins.
@Service
@RequiredArgsConstructor
public class RetentionService {
    private final RetentionPolicyRepository policyRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    // Call when a document arrives in a folder: uploaded, copied, moved or restored.
    public void applyOnEntry(Document document, UUID folderId) {
        List<Folder> chain = folderChain(folderId);
        Instant now = Instant.now();
        if (document.getArchivedAt() == null && inArchiveTray(chain)) {
            document.setArchivedAt(now);
        }

        List<RetentionPolicy> policies = policiesCovering(chain);
        if (policies.isEmpty()) {
            if (!document.isRetentionLocked(now)) {
                document.setRetentionPolicyId(null);
            }
            return;
        }

        // A lock that's over says nothing about the next one.
        if (!document.isRetentionLocked(now)) {
            document.setLockMode(null);
        }
        Instant clock = document.getArchivedAt() != null ? document.getArchivedAt() : now;
        document.setRetentionClockAt(clock);
        for (RetentionPolicy policy : policies) {
            Instant start = policy.getClockStart() == ClockStart.UPLOADED_AT ? document.getCreatedAt() : clock;
            Instant until = start.plus(Duration.ofDays(policy.getRetentionDays()));
            Instant current = document.getRetainUntil();
            if (current == null || !until.isBefore(current)) {
                document.setRetainUntil(until);
                document.setRetentionPolicyId(policy.getId());
                if (current == null || until.isAfter(current)) {
                    document.setExpiringNotifiedAt(null);
                    document.setExpiryHandledAt(null);
                }
            }
            if (policy.getLockMode() == LockMode.COMPLIANCE || document.getLockMode() == null) {
                document.setLockMode(policy.getLockMode());
            }
        }
    }

    // A folder moved here brings its documents under the policies of its new home.
    public void applyToMovedFolder(UUID folderId, UUID newParentId) {
        List<Folder> chain = folderChain(newParentId);
        if (inArchiveTray(chain)) {
            documentRepository.stampArchivedUnder(folderId, Instant.now());
        }
        policyRepository.findByFolderIdIn(chain.stream().map(Folder::getId).toList()).stream()
                .filter(RetentionPolicy::getAppliesToSubfolders)
                .forEach(policy -> applyPolicy(policy, folderId, true));
    }

    // Brings existing documents under a policy. Run when it's created or changed.
    public int applyPolicy(RetentionPolicy policy) {
        return applyPolicy(policy, policy.getFolderId(), policy.getAppliesToSubfolders());
    }

    private int applyPolicy(RetentionPolicy policy, UUID folderId, boolean includeSubfolders) {
        return documentRepository.applyRetentionPolicy(
                folderId,
                includeSubfolders,
                policy.getId(),
                policy.getRetentionDays(),
                policy.getLockMode().name(),
                policy.getClockStart().name());
    }

    // After an override, or once a lock is over: the document starts again from wherever it goes next.
    public void clearLock(Document document) {
        document.setRetainUntil(null);
        document.setLockMode(null);
        document.setRetentionPolicyId(null);
        document.setRetentionClockAt(null);
    }

    // A folder's own policy always covers it; one higher up has to reach down.
    private List<RetentionPolicy> policiesCovering(List<Folder> chain) {
        if (chain.isEmpty()) {
            return List.of();
        }
        UUID target = chain.getFirst().getId();
        List<RetentionPolicy> policies = policyRepository.findByFolderIdIn(chain.stream().map(Folder::getId).toList());
        return policies.stream()
                .filter(policy -> policy.getFolderId().equals(target) || policy.getAppliesToSubfolders())
                .toList();
    }

    // This folder, then up to its tray.
    private List<Folder> folderChain(UUID folderId) {
        List<Folder> chain = new ArrayList<>();
        Optional<Folder> current = folderRepository.findById(folderId);
        while (current.isPresent()) {
            chain.add(current.get());
            UUID parentId = current.get().getParentFolderId();
            current = parentId == null ? Optional.empty() : folderRepository.findById(parentId);
        }
        return chain;
    }

    private boolean inArchiveTray(List<Folder> chain) {
        return !chain.isEmpty() && Boolean.TRUE.equals(chain.getLast().getIsArchive());
    }
}
