package com.docket.app.controller;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.docket.app.service.ai.DocumentProcessingService;

import java.util.Map;

import java.util.UUID;

// This controller handles everything related to documents (files) in a tenant.
// All endpoints start with /api/tenants/{slug} to make sure every request
// is scoped to a specific organization.
@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DocumentProcessingService documentProcessingService;

    // ─── LIST DOCUMENTS IN A FOLDER ─────────────────────────────────────
    // GET /api/tenants/{slug}/folders/{folderId}/documents?page=0&size=20
    // Returns a paginated list of documents inside a specific folder.
    // The page/size/sort parameters are handled automatically by Spring.
    @GetMapping("/folders/{folderId}/documents")
    public ResponseEntity<Page<DocumentResponseDTO>> getDocuments(
            @PathVariable String slug,
            @PathVariable UUID folderId,
            Pageable pageable,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentService.getDocuments(slug, folderId, authentication, pageable));
    }

    // ─── UPLOAD A FILE ──────────────────────────────────────────────────
    // POST /api/tenants/{slug}/folders/{folderId}/documents
    // The file is sent as "multipart/form-data" (the same format browsers
    // use when you pick a file with an <input type="file"> element).
    // Returns 201 Created with the new document's metadata.
    @PostMapping("/folders/{folderId}/documents")
    public ResponseEntity<DocumentResponseDTO> uploadDocument(
            @PathVariable String slug,
            @PathVariable UUID folderId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        DocumentResponseDTO result = documentService.uploadDocument(slug, folderId, file, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ─── GET ONE DOCUMENT'S DETAILS ─────────────────────────────────────
    // GET /api/tenants/{slug}/documents/{docId}
    // Returns metadata about a single document (name, size, type, etc.)
    // without downloading the actual file.
    @GetMapping("/documents/{docId}")
    public ResponseEntity<DocumentResponseDTO> getDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentService.getDocumentById(slug, docId, authentication));
    }

    // ─── DOWNLOAD A FILE ────────────────────────────────────────────────
    // GET /api/tenants/{slug}/documents/{docId}/download
    // Streams the actual file content to the browser. The response headers
    // tell the browser to save it as a file (not display it inline) to
    // prevent XSS attacks from user-uploaded HTML files.
    @GetMapping("/documents/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        DocumentService.DocumentDownload download = documentService.downloadDocument(slug, docId, authentication);

        return ResponseEntity.ok()
                // Tell the browser "save this as a file" and suggest the original filename
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.document().getOriginalFileName().replace("\"", "_") + "\"")
                // Set the correct file type so the OS knows how to open it
                .contentType(MediaType.parseMediaType(download.document().getContentType()))
                // Tell the file size so the browser can show a download progress bar
                .contentLength(download.document().getFileSize())
                // Prevent the browser from trying to guess the file type
                // (a security measure against content-sniffing attacks)
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    // ─── SOFT-DELETE A DOCUMENT ─────────────────────────────────────────
    // DELETE /api/tenants/{slug}/documents/{docId}
    // Marks the document as deleted but doesn't remove the file from disk.
    // This allows recovery if someone deletes something by accident.
    // Returns 204 No Content (success, but nothing to send back).
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        documentService.softDeleteDocument(slug, docId, authentication);
        return ResponseEntity.noContent().build();
    }

    // ─── GET INDEXING STATUS ───────────────────────────────────────────
    // GET /api/tenants/{slug}/documents/{docId}/indexing-status
    // Returns the current AI processing status of a document.
    // The frontend polls this to show "Indexing..." → "Ready" transitions.
    @GetMapping("/documents/{docId}/indexing-status")
    public ResponseEntity<Map<String, String>> getIndexingStatus(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        DocumentResponseDTO doc = documentService.getDocumentById(slug, docId, authentication);
        String status = doc.getIndexingStatus() != null
                ? doc.getIndexingStatus().name()
                : "PENDING";
        return ResponseEntity.ok(Map.of("status", status));
    }

    // ─── RE-INDEX A DOCUMENT ───────────────────────────────────────────
    // POST /api/tenants/{slug}/documents/{docId}/reindex
    // Triggers re-processing of a document (deletes old chunks, creates new ones).
    // Useful if the embedding model is updated or extraction had a bug.
    @PostMapping("/documents/{docId}/reindex")
    public ResponseEntity<Map<String, String>> reindexDocument(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        if (documentProcessingService == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI processing is not available in this environment");
        }
        DocumentResponseDTO doc = documentService.getDocumentById(slug, docId, authentication);
        documentProcessingService.processDocument(doc.getId());
        return ResponseEntity.accepted().body(Map.of("status", "PROCESSING"));
    }

}
