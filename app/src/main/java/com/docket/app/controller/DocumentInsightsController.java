package com.docket.app.controller;

import com.docket.app.dto.DocumentComparisonDTO;
import com.docket.app.dto.DocumentInsightsDTO;
import com.docket.app.service.DocumentInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class DocumentInsightsController {
    private final DocumentInsightsService documentInsightsService;

    @GetMapping("/documents/{docId}/insights")
    public ResponseEntity<DocumentInsightsDTO> getInsights(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentInsightsService.getInsights(slug, docId, authentication));
    }

    @PostMapping("/documents/{docId}/insights")
    public ResponseEntity<DocumentInsightsDTO> reviewInsights(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestBody DocumentInsightsDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentInsightsService.reviewInsights(slug, docId, request, authentication));
    }

    @GetMapping("/documents/{leftId}/compare/{rightId}")
    public ResponseEntity<DocumentComparisonDTO> compareDocuments(
            @PathVariable String slug,
            @PathVariable UUID leftId,
            @PathVariable UUID rightId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentInsightsService.compareDocuments(slug, leftId, rightId, authentication));
    }
}
