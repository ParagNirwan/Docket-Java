package com.docket.app.service.ai;

import com.docket.app.enums.IndexingStatus;
import com.docket.app.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextExtractionServiceTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private TextExtractionService textExtractionService;

    @Test
    void extract_txtFile_returnsText() {
        String content = "This is a test document.";
        when(storageService.retrieve("test.txt"))
            .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        ExtractionResult result = textExtractionService.extract("test.txt", "text/plain");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertTrue(result.text().contains("This is a test document"));
    }

    @Test
    void extract_zipFile_returnsNotProcessable() {
        ExtractionResult result = textExtractionService.extract("archive.zip", "application/zip");

        assertEquals(IndexingStatus.NOT_PROCESSABLE, result.status());
        assertNull(result.text());
    }

    @Test
    void extract_unknownType_returnsNotProcessable() {
        ExtractionResult result = textExtractionService.extract("file.xyz", "application/x-unknown");

        assertEquals(IndexingStatus.NOT_PROCESSABLE, result.status());
    }
    @Test
void extract_pdfFile_returnsText() throws Exception {
    // Tika can extract from any InputStream — even a plain text one
    // passed with a PDF content type. For a true PDF test, we use
    // a real PDF file. For now, we verify the routing works.
    String content = "PDF document content here.";
    when(storageService.retrieve("doc.pdf"))
        .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    ExtractionResult result = textExtractionService.extract("doc.pdf", "application/pdf");

    assertEquals(IndexingStatus.COMPLETED, result.status());
    assertNotNull(result.text());
}

}
