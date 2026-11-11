package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.GenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSummaryServiceTest {

    private static final String MODEL_REPLY = """
            {"summary": "A lease between Acme and Bolt.", "keyPoints": ["Ends May 2027", "Rent 2,000 a month"]}
            """;

    @Mock private GeminiClient geminiClient;
    @Mock private EmbeddingService embeddingService;
    @Mock private DocumentRepository documentRepository;
    @Mock private TenantRepository tenantRepository;

    private DocumentSummaryService service;
    private Document document;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new DocumentSummaryService(
                geminiClient, embeddingService, documentRepository, tenantRepository, new ObjectMapper());
        tenant = new Tenant();
        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(UUID.randomUUID());
        document.setOriginalFileName("lease.pdf");
        when(tenantRepository.findById(document.getTenantId())).thenReturn(Optional.of(tenant));
    }

    private TextChunk chunk(String text) {
        return new TextChunk(text, 1, 1, null, 0, text.length());
    }

    @Test
    void refresh_storesSummaryKeyPointsAndAnEmbeddingOfBoth() throws IOException {
        when(geminiClient.generate(any(), anyString(), any(GenerationConfig.class))).thenReturn(MODEL_REPLY);
        ArgumentCaptor<List<String>> embedded = ArgumentCaptor.captor();
        when(embeddingService.embed(embedded.capture(), eq(EmbeddingTaskType.RETRIEVAL_DOCUMENT)))
                .thenReturn(List.of(new float[]{0.5f, 0.25f}));

        service.refresh(document, List.of(chunk("The lease ends in May 2027.")));

        verify(documentRepository).saveAiSummary(document.getId(), "A lease between Acme and Bolt.",
                "[\"Ends May 2027\",\"Rent 2,000 a month\"]", "[0.5,0.25]");
        assertTrue(embedded.getValue().get(0).contains("lease.pdf"));
        assertTrue(embedded.getValue().get(0).contains("Ends May 2027"));
        verify(geminiClient, times(1)).generate(any(), anyString(), any(GenerationConfig.class));
    }

    @Test
    void refresh_longDocument_condensesItInGroupsFirst() throws IOException {
        // 25 chunks of 2,000 characters is past the single request limit: 3 groups, then the summary
        List<TextChunk> chunks = IntStream.range(0, 25).mapToObj(i -> chunk("x".repeat(2000))).toList();
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompts.capture(), any(GenerationConfig.class)))
                .thenReturn("notes", "notes", "notes", MODEL_REPLY);
        when(embeddingService.embed(any(), any())).thenReturn(List.of(new float[]{0.5f}));

        service.refresh(document, chunks);

        assertEquals(4, prompts.getAllValues().size());
        assertTrue(prompts.getAllValues().get(3).contains("notes"));
        verify(documentRepository).saveAiSummary(eq(document.getId()), anyString(), anyString(), anyString());
    }

    @Test
    void refresh_organizationSwitchedSummariesOff_removesTheOldOneAndSkipsTheModel() {
        tenant.setAiSummariesEnabled(false);

        service.refresh(document, List.of(chunk("text")));

        verify(documentRepository).clearAiSummary(document.getId());
        verifyNoInteractions(geminiClient, embeddingService);
    }

    @Test
    void refresh_switchedOffForEveryone_skipsTheModel() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.refresh(document, List.of(chunk("text")));

        verify(documentRepository).clearAiSummary(document.getId());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void refresh_documentWithNoText_removesTheOldSummary() {
        service.refresh(document, List.of());

        verify(documentRepository).clearAiSummary(document.getId());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void refresh_modelFails_doesNotThrowAndLeavesNoStaleSummary() throws IOException {
        when(geminiClient.generate(any(), anyString(), any(GenerationConfig.class)))
                .thenThrow(new IOException("down"));

        service.refresh(document, List.of(chunk("text")));

        verify(documentRepository).clearAiSummary(document.getId());
        verify(documentRepository, never()).saveAiSummary(any(), any(), any(), any());
    }
}
