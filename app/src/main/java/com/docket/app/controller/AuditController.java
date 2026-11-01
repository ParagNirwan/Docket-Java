package com.docket.app.controller;

import com.docket.app.dto.AuditEventDTO;
import com.docket.app.enums.AuditAction;
import com.docket.app.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

// The organization's activity log. Only Owners and Admins hold AUDIT_READ.
@RestController
@RequestMapping("/api/tenants/{slug}/audit-events")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    // GET /api/tenants/{slug}/audit-events?actor=&action=&from=&to=&page=0&size=50
    @GetMapping
    public ResponseEntity<Page<AuditEventDTO>> search(
            @PathVariable String slug,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        AuditService.Filter filter = new AuditService.Filter(actor, action, from, to);
        return ResponseEntity.ok(auditService.search(slug, filter, page, size, authentication));
    }

    // GET /api/tenants/{slug}/audit-events/export
    // Writes straight to the response so a long log isn't held in memory.
    @GetMapping("/export")
    public void export(
            @PathVariable String slug,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"");
        AuditService.Filter filter = new AuditService.Filter(actor, action, from, to);
        auditService.exportCsv(slug, filter, authentication, response.getWriter());
    }
}
