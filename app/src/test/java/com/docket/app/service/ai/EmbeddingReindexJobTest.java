package com.docket.app.service.ai;

import com.docket.app.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingReindexJobTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @InjectMocks
    private EmbeddingReindexJob job;

    @Test
    void reindexNextBatch_processesWhatItClaimed() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(documentRepository.claimForReindex(eq(1), eq(3), any(), eq(10))).thenReturn(List.of(first, second));

        job.reindexNextBatch();

        verify(documentProcessingService).processDocument(first);
        verify(documentProcessingService).processDocument(second);
    }

    @Test
    void reindexNextBatch_disabled_doesNothing() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.reindexNextBatch();

        verifyNoInteractions(documentRepository, documentProcessingService);
    }
}
