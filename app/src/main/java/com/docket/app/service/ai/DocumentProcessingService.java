package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.entity.DocumentChunk;
import com.docket.app.enums.IndexingStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.repository.DocumentChunkRepository;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextExtractionService textExtractionService;
    private final ChunkingService chunkingService;
    private final ChunkEnrichmentService chunkEnrichmentService;
    private final EmbeddingService embeddingService;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;
    private final TenantRepository tenantRepository;

    // Raising this makes the re-index job process every document again. Do that whenever
    // a change to extraction, chunking or embedding should reach existing documents.
    @Value("${docket.ai.embedding.version}")
    private int embeddingVersion = 1;

    @Async
    @EventListener
    public void handleDocumentUploaded(DocumentUploadedEvent event) {
        processDocument(event.documentId());
    }

    @Async
    public void processDocumentAsync(UUID documentId) {
        processDocument(documentId);
    }

    public void processDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document not found for processing: {}", documentId);
            return;
        }

        log.info("Processing document: {} ({})", document.getOriginalFileName(), documentId);

        // A document that is already searchable stays searchable on its old chunks
        // until the new ones are ready, and keeps them if re-processing fails.
        boolean alreadySearchable = document.getIndexingStatus() == IndexingStatus.COMPLETED;
        if (!alreadySearchable) {
            documentRepository.updateIndexingStatus(documentId, IndexingStatus.PROCESSING);
        }

        try {
            ExtractionResult extraction = textExtractionService.extract(
                    document.getStoredFileName(), document.getContentType());

            if (extraction.status() == IndexingStatus.NOT_PROCESSABLE) {
                replaceChunks(documentId, List.of(), IndexingStatus.NOT_PROCESSABLE);
                log.info("Document not processable: {}", documentId);
                return;
            }

            if (extraction.status() == IndexingStatus.FAILED) {
                markFailed(document, alreadySearchable);
                return;
            }

            List<TextChunk> chunks = chunkingService.chunk(extraction.segments());

            if (chunks.isEmpty()) {
                replaceChunks(documentId, List.of(), IndexingStatus.COMPLETED);
                log.info("No text content to index for document: {}", documentId);
                return;
            }

            List<String> embeddingTexts = chunkEnrichmentService.buildEmbeddingTexts(document, chunks);
            List<float[]> embeddings = embeddingService.embed(embeddingTexts, EmbeddingTaskType.RETRIEVAL_DOCUMENT);

            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                DocumentChunk entity = new DocumentChunk();
                entity.setTenantId(document.getTenantId());
                entity.setDocumentId(documentId);
                entity.setChunkIndex(i);
                entity.setChunkText(chunk.text());
                entity.setPageStart(chunk.pageStart());
                entity.setPageEnd(chunk.pageEnd());
                entity.setHeading(chunk.heading());
                entity.setCharStart(chunk.charStart());
                entity.setCharEnd(chunk.charEnd());
                entity.setEmbedding(embeddings.get(i));
                chunkEntities.add(entity);
            }

            replaceChunks(documentId, chunkEntities, IndexingStatus.COMPLETED);
            log.info("Document processing completed: {} ({} chunks)", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Document processing failed: {}", documentId, e);
            markFailed(document, alreadySearchable);
        }
    }

    // All in one transaction, so a search never sees the document with no chunks
    private void replaceChunks(UUID documentId, List<DocumentChunk> chunks, IndexingStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            documentChunkRepository.deleteByDocumentId(documentId);
            documentChunkRepository.saveAll(chunks);
            documentRepository.markIndexed(documentId, status.name(), embeddingVersion);
        });
    }

    private void markFailed(Document document, boolean alreadySearchable) {
        if (!alreadySearchable) {
            documentRepository.updateIndexingStatus(document.getId(), IndexingStatus.FAILED);
            notifyUploader(document);
        }
    }

    // Telling someone is a courtesy, so a problem here can't fail the processing.
    private void notifyUploader(Document document) {
        if (document.getUploadedBy() == null) {
            return;
        }
        try {
            tenantRepository.findById(document.getTenantId()).ifPresent(tenant ->
                    notificationService.notify(tenant.getId(), document.getUploadedBy(), NotificationType.INDEXING_FAILED,
                            "AI search couldn't read " + document.getOriginalFileName(),
                            "It's safely stored, but won't appear in AI answers. You can try re-indexing it.",
                            NotificationService.workspaceLink(tenant.getSlug(), "review", null)));
        } catch (RuntimeException e) {
            log.warn("Could not send the indexing failure notification for {}", document.getId(), e);
        }
    }
}
