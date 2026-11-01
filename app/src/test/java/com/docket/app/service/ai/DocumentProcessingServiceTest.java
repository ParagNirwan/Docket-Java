package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.entity.DocumentChunk;
import com.docket.app.enums.IndexingStatus;
import com.docket.app.repository.DocumentChunkRepository;
import com.docket.app.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private TextExtractionService textExtractionService;
    @Mock private ChunkEnrichmentService chunkEnrichmentService;
    @Mock private EmbeddingService embeddingService;
    @Mock private com.docket.app.service.NotificationService notificationService;
    @Mock private com.docket.app.repository.TenantRepository tenantRepository;

    private DocumentProcessingService service;
    private final UUID documentId = UUID.randomUUID();
    private Document document;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingService(
                documentRepository, documentChunkRepository, textExtractionService, new ChunkingService(),
                chunkEnrichmentService, embeddingService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), notificationService, tenantRepository);

        document = new Document();
        document.setId(documentId);
        document.setTenantId(UUID.randomUUID());
        document.setStoredFileName("stored.pdf");
        document.setContentType("application/pdf");
        document.setIndexingStatus(IndexingStatus.PENDING);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    }

    private void extractionSucceeds() {
        when(textExtractionService.extract("stored.pdf", "application/pdf")).thenReturn(
                ExtractionResult.success(List.of(new TextSegment("Pay within 30 days.", 4, "Payment Terms"))));
    }

    private void embeddingSucceeds() {
        when(chunkEnrichmentService.buildEmbeddingTexts(eq(document), anyList()))
                .thenReturn(List.of("Document: a.pdf\n\nPay within 30 days."));
        when(embeddingService.embed(anyList(), eq(EmbeddingTaskType.RETRIEVAL_DOCUMENT)))
                .thenReturn(List.of(new float[]{0.1f}));
    }

    @Test
    void processDocument_embedsEnrichedTextButStoresOriginalWithItsLocation() {
        extractionSucceeds();
        embeddingSucceeds();

        service.processDocument(documentId);

        verify(embeddingService).embed(
                List.of("Document: a.pdf\n\nPay within 30 days."), EmbeddingTaskType.RETRIEVAL_DOCUMENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> saved = ArgumentCaptor.forClass(List.class);
        verify(documentChunkRepository).saveAll(saved.capture());
        DocumentChunk chunk = saved.getValue().get(0);
        assertEquals("Pay within 30 days.", chunk.getChunkText());
        assertEquals(4, chunk.getPageStart());
        assertEquals(4, chunk.getPageEnd());
        assertEquals("Payment Terms", chunk.getHeading());
    }

    @Test
    void processDocument_newDocument_showsProcessingThenRecordsVersion() {
        extractionSucceeds();
        embeddingSucceeds();

        service.processDocument(documentId);

        InOrder order = inOrder(documentRepository, documentChunkRepository);
        order.verify(documentRepository).updateIndexingStatus(documentId, IndexingStatus.PROCESSING);
        order.verify(documentChunkRepository).deleteByDocumentId(documentId);
        order.verify(documentChunkRepository).saveAll(anyList());
        order.verify(documentRepository).markIndexed(documentId, "COMPLETED", 1);
    }

    @Test
    void processDocument_reindexing_staysSearchableThroughout() {
        document.setIndexingStatus(IndexingStatus.COMPLETED);
        extractionSucceeds();
        embeddingSucceeds();

        service.processDocument(documentId);

        verify(documentRepository, never()).updateIndexingStatus(any(), any());
        verify(documentRepository).markIndexed(documentId, "COMPLETED", 1);
    }

    @Test
    void processDocument_reindexFails_keepsOldChunksAndStatus() {
        document.setIndexingStatus(IndexingStatus.COMPLETED);
        extractionSucceeds();
        when(chunkEnrichmentService.buildEmbeddingTexts(eq(document), anyList())).thenReturn(List.of("text"));
        when(embeddingService.embed(anyList(), any())).thenThrow(new RuntimeException("quota"));

        service.processDocument(documentId);

        verify(documentChunkRepository, never()).deleteByDocumentId(any());
        verify(documentRepository, never()).updateIndexingStatus(any(), any());
        verify(documentRepository, never()).markIndexed(any(), anyString(), anyInt());
    }

    @Test
    void processDocument_newDocumentFails_isMarkedFailed() {
        when(textExtractionService.extract("stored.pdf", "application/pdf")).thenReturn(ExtractionResult.failed());

        service.processDocument(documentId);

        verify(documentRepository).updateIndexingStatus(documentId, IndexingStatus.FAILED);
        verify(documentRepository, never()).markIndexed(any(), anyString(), anyInt());
    }

    @Test
    void processDocument_notProcessable_recordsVersionSoItIsNotRetried() {
        when(textExtractionService.extract("stored.pdf", "application/pdf"))
                .thenReturn(ExtractionResult.notProcessable());

        service.processDocument(documentId);

        verify(documentRepository).markIndexed(documentId, "NOT_PROCESSABLE", 1);
    }
}
