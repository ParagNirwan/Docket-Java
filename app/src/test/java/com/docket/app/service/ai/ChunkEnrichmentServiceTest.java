package com.docket.app.service.ai;

import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.repository.FolderRepository;
import com.google.cloud.vertexai.api.GenerationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkEnrichmentServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private GeminiClient geminiClient;

    @InjectMocks
    private ChunkEnrichmentService service;

    private Document document;

    @BeforeEach
    void setUp() {
        UUID trayId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        Folder tray = new Folder();
        tray.setName("Clients");
        Folder folder = new Folder();
        folder.setName("Acme");
        folder.setParentFolderId(trayId);
        when(folderRepository.findById(trayId)).thenReturn(Optional.of(tray));
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));

        document = new Document();
        document.setOriginalFileName("lease.pdf");
        document.setFolderId(folderId);
    }

    private TextChunk chunk(String heading) {
        return new TextChunk("The term is 24 months.", 1, 1, heading, 0, 22);
    }

    @Test
    void buildEmbeddingTexts_putsDocumentContextBeforeTheText() {
        assertEquals(List.of("""
                Document: lease.pdf
                Folder: Clients / Acme
                Section: Term

                The term is 24 months."""), service.buildEmbeddingTexts(document, List.of(chunk("Term"))));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void buildEmbeddingTexts_leavesOutSectionWhenThereIsNoHeading() {
        assertEquals(List.of("""
                Document: lease.pdf
                Folder: Clients / Acme

                The term is 24 months."""), service.buildEmbeddingTexts(document, List.of(chunk(null))));
    }

    @Test
    void buildEmbeddingTexts_llmContextEnabled_addsIt() throws IOException {
        ReflectionTestUtils.setField(service, "llmContextEnabled", true);
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenReturn(" From the Acme lease. ");

        assertEquals(List.of("""
                Document: lease.pdf
                Folder: Clients / Acme
                Context: From the Acme lease.

                The term is 24 months."""), service.buildEmbeddingTexts(document, List.of(chunk(null))));
    }

    @Test
    void buildEmbeddingTexts_llmContextFails_stillIndexes() throws IOException {
        ReflectionTestUtils.setField(service, "llmContextEnabled", true);
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenThrow(new IOException("down"));

        assertEquals(List.of("""
                Document: lease.pdf
                Folder: Clients / Acme

                The term is 24 months."""), service.buildEmbeddingTexts(document, List.of(chunk(null))));
    }

    @Test
    void buildEmbeddingTexts_documentOverLlmLimit_skipsTheModel() {
        ReflectionTestUtils.setField(service, "llmContextEnabled", true);
        ReflectionTestUtils.setField(service, "llmContextMaxDocumentChars", 5);

        service.buildEmbeddingTexts(document, List.of(chunk(null)));

        verifyNoInteractions(geminiClient);
    }
}
