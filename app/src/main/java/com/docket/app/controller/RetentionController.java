package com.docket.app.controller;

import com.docket.app.dto.RetentionPolicyDTO;
import com.docket.app.dto.RetentionPolicyRequestDTO;
import com.docket.app.dto.RetentionSummaryDTO;
import com.docket.app.dto.TrashSettingsDTO;
import com.docket.app.service.RetentionPolicyService;
import com.docket.app.service.TrashService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class RetentionController {
    private final RetentionPolicyService retentionPolicyService;
    private final com.docket.app.service.RetentionReportService retentionReportService;
    private final TrashService trashService;

    @GetMapping("/retention-policies")
    public ResponseEntity<List<RetentionPolicyDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(retentionPolicyService.list(slug, authentication));
    }

    @GetMapping("/retention-policies/summary")
    public ResponseEntity<RetentionSummaryDTO> summary(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(retentionPolicyService.summary(slug, authentication));
    }

    @GetMapping("/retention-policies/{policyId}")
    public ResponseEntity<RetentionPolicyDTO> get(
            @PathVariable String slug,
            @PathVariable UUID policyId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(retentionPolicyService.get(slug, policyId, authentication));
    }

    @PostMapping("/retention-policies")
    public ResponseEntity<RetentionPolicyDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody RetentionPolicyRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(retentionPolicyService.create(slug, request, authentication));
    }

    @PutMapping("/retention-policies/{policyId}")
    public ResponseEntity<RetentionPolicyDTO> update(
            @PathVariable String slug,
            @PathVariable UUID policyId,
            @Valid @RequestBody RetentionPolicyRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(retentionPolicyService.update(slug, policyId, request, authentication));
    }

    @DeleteMapping("/retention-policies/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable UUID policyId,
            Authentication authentication
    ) {
        retentionPolicyService.delete(slug, policyId, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash-settings")
    public ResponseEntity<TrashSettingsDTO> getTrashSettings(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(trashService.getSettings(slug, authentication));
    }

    @PutMapping("/trash-settings")
    public ResponseEntity<TrashSettingsDTO> updateTrashSettings(
            @PathVariable String slug,
            @Valid @RequestBody TrashSettingsDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(trashService.updateSettings(slug, request, authentication));
    }

    // GET /retention-policies/preview?folderId=&retentionDays=&lockMode=&clockStart=&appliesToSubfolders=
    // Dry run: what a policy with these terms would lock, extend or leave alone. Changes nothing.
    @GetMapping("/retention-policies/preview")
    public ResponseEntity<com.docket.app.service.RetentionReportService.Preview> preview(
            @PathVariable String slug,
            @org.springframework.web.bind.annotation.RequestParam java.util.UUID folderId,
            @org.springframework.web.bind.annotation.RequestParam int retentionDays,
            @org.springframework.web.bind.annotation.RequestParam com.docket.app.enums.LockMode lockMode,
            @org.springframework.web.bind.annotation.RequestParam com.docket.app.enums.ClockStart clockStart,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean appliesToSubfolders,
            Authentication authentication
    ) {
        return ResponseEntity.ok(retentionReportService.preview(slug, folderId, retentionDays, lockMode, clockStart,
                appliesToSubfolders, authentication));
    }

    // GET /retention-policies/report — CSV of every document under a retention lock or legal hold
    @GetMapping("/retention-policies/report")
    public void complianceReport(@PathVariable String slug, Authentication authentication,
                                 jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"retention-compliance-report.csv\"");
        retentionReportService.writeComplianceReport(slug, authentication, response.getWriter());
    }
}
