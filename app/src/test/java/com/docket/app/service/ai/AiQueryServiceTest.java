package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentChunkRepository;
import com.docket.app.repository.DocumentRepository;
import com.google.cloud.vertexai.VertexAI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQueryServiceTest {
    private static final String QUESTION = "What is the termination clause?";

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private VertexAI vertexAI;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private AiQueryService aiQueryService;

    private UUID tenantId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        documentId = UUID.randomUUID();
    }

    @Test
    void query_softDeletedDocument_throws404() {
        Document document = new Document();
        document.setId(documentId);
        document.setTenantId(tenantId);
        document.setFileStatus(FileStatus.SOFT_DELETED);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, documentChunkRepository);
    }

    @Test
    void query_documentNotFoundOrOtherTenant_throws404() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aiQueryService.query(tenantId, QUESTION, documentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(embeddingService, documentChunkRepository);
    }
}
