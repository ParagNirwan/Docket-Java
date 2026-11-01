package com.docket.app.service;

import com.docket.app.dto.SearchResultDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentSearchRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Classic search across a workspace, by file name and by the text inside documents.
@Service
@RequiredArgsConstructor
public class DocumentSearchService {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_PAGE_SIZE = 50;

    // The file types a person can filter by, as patterns for the end of a file name.
    private static final Map<String, String> TYPE_PATTERNS = Map.of(
            "pdf", "\\.pdf$",
            "word", "\\.docx?$",
            "spreadsheet", "\\.(xlsx?|csv)$",
            "presentation", "\\.pptx?$",
            "image", "\\.(png|jpe?g|gif|svg|webp|tiff?)$",
            "text", "\\.txt$",
            "archive", "\\.(zip|rar|7z)$");

    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final DocumentSearchRepository documentSearchRepository;

    public Page<SearchResultDTO> search(String slug, Filter filter, int page, int size, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        String query = filter.query() == null ? "" : filter.query().trim();
        if (query.length() < MIN_QUERY_LENGTH || query.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search for between 2 and 200 characters");
        }

        String fileNamePattern = null;
        if (filter.type() != null && !filter.type().isBlank()) {
            fileNamePattern = TYPE_PATTERNS.get(filter.type());
            if (fileNamePattern == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown file type");
            }
        }

        var criteria = new DocumentSearchRepository.Criteria(
                tenant.getId(),
                query,
                folderAccessService.canAccessRestrictedTrays(authentication, tenant.getId()),
                fileNamePattern,
                filter.trayId(),
                filter.from(),
                filter.to());
        return documentSearchRepository.search(criteria, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    public record Filter(String query, String type, UUID trayId, Instant from, Instant to) {
    }
}
