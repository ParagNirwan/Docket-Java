package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Retention dry runs ("what would this policy do?") and the compliance report.
@Service
@RequiredArgsConstructor
public class RetentionReportService {
    private final TenantRepository tenantRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    public record Preview(long covered, long newlyLocked, long extended, long unchanged, long alreadyExpired,
                          long becomeCompliance, Instant earliestUntil, Instant latestUntil) {
    }

    // Changes nothing. Same selection and arithmetic as actually applying the policy.
    public Preview preview(String slug, UUID folderId, int retentionDays, LockMode lockMode, ClockStart clockStart,
                           boolean appliesToSubfolders, Authentication authentication) {
        Tenant tenant = requireTenant(slug, authentication, Permission.RETENTION_READ);
        folderRepository.findById(folderId)
                .filter(folder -> tenant.getId().equals(folder.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (retentionDays < 1 || retentionDays > 36_500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retention must be between 1 day and 100 years");
        }
        var row = documentRepository.previewRetentionPolicy(tenant.getId(), folderId, appliesToSubfolders,
                retentionDays, lockMode.name(), clockStart.name());
        return new Preview(row.getCovered(), row.getNewlyLocked(), row.getExtended(), row.getUnchanged(),
                row.getAlreadyExpired(), row.getBecomeCompliance(), instant(row.getEarliestUntilEpoch()), instant(row.getLatestUntilEpoch()));
    }

    public void writeComplianceReport(String slug, Authentication authentication, Writer writer) throws IOException {
        Tenant tenant = requireTenant(slug, authentication, Permission.RETENTION_READ);
        auditService.record(authentication, tenant.getId(), AuditAction.AUDIT_LOG_EXPORTED, "TENANT", tenant.getId(),
                Map.of("report", "retention_compliance"));

        List<Object[]> rows = documentRepository.lockedDocumentsReport(tenant.getId());
        writer.write("document_id,file_name,folder,lock_mode,retain_until,retention_policy_id,uploaded_at,archived_at,legal_hold\r\n");
        for (Object[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) line.append(',');
                line.append(AuditService.csv(row[i]));
            }
            writer.write(line.append("\r\n").toString());
        }
        writer.flush();
    }

    private static Instant instant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private Tenant requireTenant(String slug, Authentication authentication, Permission permission) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), permission);
        return tenant;
    }
}
