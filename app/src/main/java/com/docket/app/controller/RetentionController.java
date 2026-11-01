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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class RetentionController {
    private final RetentionPolicyService retentionPolicyService;
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
}
