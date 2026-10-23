package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.entity.DocumentChunk;
import com.docket.app.enums.IndexingStatus;
import com.docket.app.repository.DocumentChunkRepository;
import com.docket.app.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final EmbeddingService embeddingService;

    @Async
    @EventListener
    public void handleDocumentUploaded(DocumentUploadedEvent event) {
        processDocument(event.documentId());
    }

    public void processDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document not found for processing: {}", documentId);
            return;
        }

        log.info("Processing document: {} ({})", document.getOriginalFileName(), documentId);
        updateStatus(document, IndexingStatus.PROCESSING);

        try {
            ExtractionResult extraction = textExtractionService.extract(
                    document.getStoredFileName(), document.getContentType());

            if (extraction.status() == IndexingStatus.NOT_PROCESSABLE) {
                updateStatus(document, IndexingStatus.NOT_PROCESSABLE);
                log.info("Document not processable: {}", documentId);
                return;
            }

            if (extraction.status() == IndexingStatus.FAILED) {
                updateStatus(document, IndexingStatus.FAILED);
                return;
            }

            List<String> chunks = chunkingService.chunk(extraction.text());

            if (chunks.isEmpty()) {
                updateStatus(document, IndexingStatus.COMPLETED);
                log.info("No text content to index for document: {}", documentId);
                return;
            }

            List<float[]> embeddings = embeddingService.embed(chunks);

            documentChunkRepository.deleteByDocumentId(documentId);

            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setTenantId(document.getTenantId());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setChunkText(chunks.get(i));
                chunk.setEmbedding(embeddings.get(i));
                chunkEntities.add(chunk);
            }

            documentChunkRepository.saveAll(chunkEntities);

            updateStatus(document, IndexingStatus.COMPLETED);
            log.info("Document processing completed: {} ({} chunks)", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Document processing failed: {}", documentId, e);
            updateStatus(document, IndexingStatus.FAILED);
        }
    }

    private void updateStatus(Document document, IndexingStatus status) {
        document.setIndexingStatus(status);
        documentRepository.save(document);
    }
}
