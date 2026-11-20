package com.docket.app.controller;

import com.docket.app.service.ai.DocumentCompareService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// Redline comparison of two documents, or two versions of one (#84). Text extraction runs
// with the gcp profile only, so elsewhere this answers 501 instead of failing startup.
@RestController
@RequestMapping("/api/tenants/{slug}/documents")
@RequiredArgsConstructor
public class DocumentCompareController {
    private final ObjectProvider<DocumentCompareService> compareService;

    // GET …/documents/{leftId}/diff/{rightId}?leftVersion=&rightVersion=&summary=true
    @GetMapping("/{leftId}/diff/{rightId}")
    public ResponseEntity<DocumentCompareService.Comparison> diff(
            @PathVariable String slug,
            @PathVariable UUID leftId,
            @PathVariable UUID rightId,
            @RequestParam(required = false) Integer leftVersion,
            @RequestParam(required = false) Integer rightVersion,
            @RequestParam(defaultValue = "false") boolean summary,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service().compare(slug, leftId, leftVersion, rightId, rightVersion, summary, authentication));
    }

    // GET …/documents/{id}/versions/{a}/diff/{b}?summary=true
    @GetMapping("/{id}/versions/{fromVersion}/diff/{toVersion}")
    public ResponseEntity<DocumentCompareService.Comparison> versionDiff(
            @PathVariable String slug,
            @PathVariable UUID id,
            @PathVariable int fromVersion,
            @PathVariable int toVersion,
            @RequestParam(defaultValue = "false") boolean summary,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service().compare(slug, id, fromVersion, id, toVersion, summary, authentication));
    }

    private DocumentCompareService service() {
        DocumentCompareService service = compareService.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Document comparison needs the AI services (gcp profile)");
        }
        return service;
    }
}
