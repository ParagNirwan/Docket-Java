package com.docket.app.service;

import com.docket.app.dto.CreateTagRequestDTO;
import com.docket.app.dto.TagDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tag;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TagRepository;
import com.docket.app.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TagRepository tagRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private FolderAccessService folderAccessService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TagService tagService;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(SLUG);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
    }

    @Test
    void getTags_returnsAllTenantTagsSorted() {
        Tag tag1 = new Tag(tenantId, "Finance", "#112233");
        Tag tag2 = new Tag(tenantId, "Legal", "#445566");
        when(tagRepository.findByTenantIdOrderByNameAsc(tenantId)).thenReturn(List.of(tag1, tag2));

        List<TagDTO> result = tagService.getTags(SLUG, authentication);

        assertEquals(2, result.size());
        assertEquals("Finance", result.get(0).name());
        verify(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.DOCUMENTS_READ);
    }

    @Test
    void createTag_savesAndAudits() {
        when(tagRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Finance")).thenReturn(false);
        Tag saved = new Tag(tenantId, "Finance", "#112233");
        saved.setId(UUID.randomUUID());
        when(tagRepository.save(any(Tag.class))).thenReturn(saved);

        TagDTO result = tagService.createTag(SLUG, new CreateTagRequestDTO("Finance", "#112233"), authentication);

        assertEquals("Finance", result.name());
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.TAG_CREATED), eq("TAG"), eq(saved.getId()), any());
    }

    @Test
    void createTag_rejectsDuplicates() {
        when(tagRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Finance")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tagService.createTag(SLUG, new CreateTagRequestDTO("Finance", "#112233"), authentication));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(tagRepository, never()).save(any());
    }

    @Test
    void deleteTag_removesTag() {
        UUID tagId = UUID.randomUUID();
        Tag tag = new Tag(tenantId, "Finance", "#112233");
        tag.setId(tagId);
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        tagService.deleteTag(SLUG, tagId, authentication);

        verify(tagRepository).delete(tag);
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.TAG_DELETED), eq("TAG"), eq(tagId), any());
    }

    @Test
    void addTagToDocument_addsAndReturnsUpdatedTags() {
        UUID docId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        Document doc = new Document();
        doc.setId(docId);
        doc.setTenantId(tenantId);
        doc.setFolderId(folderId);
        doc.setTags(new HashSet<>());

        Tag tag = new Tag(tenantId, "HR", "#aabbcc");
        tag.setId(tagId);

        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.of(doc));
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        List<TagDTO> tags = tagService.addTagToDocument(SLUG, docId, tagId, authentication);

        assertEquals(1, tags.size());
        assertEquals("HR", tags.get(0).name());
        verify(documentRepository).save(doc);
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_TAGGED), eq("DOCUMENT"), eq(docId), any());
    }
}

