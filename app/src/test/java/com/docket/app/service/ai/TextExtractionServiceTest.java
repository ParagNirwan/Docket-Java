package com.docket.app.service.ai;

import com.docket.app.enums.IndexingStatus;
import com.docket.app.service.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextExtractionServiceTest {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Mock
    private StorageService storageService;

    @Mock
    private VisionOcrService visionOcrService;

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
    void extract_pdf_keepsPageNumbers() throws Exception {
        String pageText = "This page has plenty of ordinary text on it, more than a scan would have. ";
        when(storageService.retrieve("doc.pdf"))
            .thenReturn(new ByteArrayInputStream(pdf(pageText + "First", pageText + "Second")));

        ExtractionResult result = textExtractionService.extract("doc.pdf", "application/pdf");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertEquals(2, result.segments().size());
        assertEquals(1, result.segments().get(0).page());
        assertTrue(result.segments().get(0).text().contains("First"));
        assertEquals(2, result.segments().get(1).page());
        assertTrue(result.segments().get(1).text().contains("Second"));
        verifyNoInteractions(visionOcrService);
    }

    @Test
    void extract_scannedPdf_usesOcr() throws Exception {
        List<TextSegment> ocrText = List.of(new TextSegment("Read by OCR", 1, null));
        when(storageService.retrieve("scan.pdf")).thenReturn(new ByteArrayInputStream(pdf("", "")));
        when(visionOcrService.readPdf("scan.pdf")).thenReturn(ocrText);

        ExtractionResult result = textExtractionService.extract("scan.pdf", "application/pdf");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertEquals(ocrText, result.segments());
    }

    @Test
    void extract_scannedPdfOverPageLimit_failsWithoutCallingOcr() throws Exception {
        ReflectionTestUtils.setField(textExtractionService, "ocrMaxPages", 1);
        when(storageService.retrieve("scan.pdf")).thenReturn(new ByteArrayInputStream(pdf("", "")));

        ExtractionResult result = textExtractionService.extract("scan.pdf", "application/pdf");

        assertEquals(IndexingStatus.FAILED, result.status());
        verifyNoInteractions(visionOcrService);
    }

    @Test
    void extract_scannedPdfOverPageLimit_keepsWhateverTextItHas() throws Exception {
        ReflectionTestUtils.setField(textExtractionService, "ocrMaxPages", 1);
        when(storageService.retrieve("scan.pdf")).thenReturn(new ByteArrayInputStream(pdf("Page stamp", "")));

        ExtractionResult result = textExtractionService.extract("scan.pdf", "application/pdf");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertTrue(result.text().contains("Page stamp"));
        verifyNoInteractions(visionOcrService);
    }

    @Test
    void extract_ocrFailure_returnsFailed() throws Exception {
        when(storageService.retrieve("scan.pdf")).thenReturn(new ByteArrayInputStream(pdf("")));
        when(visionOcrService.readPdf("scan.pdf")).thenThrow(new IllegalStateException("quota exceeded"));

        assertEquals(IndexingStatus.FAILED, textExtractionService.extract("scan.pdf", "application/pdf").status());
    }

    @Test
    void extract_image_usesOcr() {
        when(visionOcrService.readImage("receipt.png")).thenReturn(List.of(new TextSegment("Total 12.50", null, null)));

        ExtractionResult result = textExtractionService.extract("receipt.png", "image/png");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertEquals("Total 12.50", result.text());
        verifyNoInteractions(storageService);
    }

    @Test
    void extract_imageOcrFailure_returnsFailed() {
        when(visionOcrService.readImage("receipt.png")).thenThrow(new IllegalStateException("bad image"));

        assertEquals(IndexingStatus.FAILED, textExtractionService.extract("receipt.png", "image/png").status());
    }

    @Test
    void extract_docx_keepsNearestHeading() throws Exception {
        when(storageService.retrieve("doc.docx")).thenReturn(new ByteArrayInputStream(docx()));

        ExtractionResult result = textExtractionService.extract("doc.docx", DOCX);

        assertEquals(IndexingStatus.COMPLETED, result.status());
        TextSegment payment = result.segments().stream()
                .filter(segment -> segment.text().contains("Pay within 30 days"))
                .findFirst().orElseThrow();
        assertEquals("Payment Terms", payment.heading());
        assertNull(payment.page());
    }

    @Test
    void extract_textOverTheLimit_keepsTheStart() {
        ReflectionTestUtils.setField(textExtractionService, "maxChars", 1000);
        String content = "word ".repeat(1000);
        when(storageService.retrieve("big.txt"))
            .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        ExtractionResult result = textExtractionService.extract("big.txt", "text/plain");

        assertEquals(IndexingStatus.COMPLETED, result.status());
        assertTrue(result.text().length() <= 1000);
    }

    @Test
    void extract_textLongerThanOldTikaLimit_isNotCutShort() {
        // The Tika facade used before stopped at 100,000 characters without saying so.
        String content = "word ".repeat(30_000) + "THE-END";
        when(storageService.retrieve("big.txt"))
            .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertTrue(textExtractionService.extract("big.txt", "text/plain").text().contains("THE-END"));
    }

    private byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                        stream.newLineAtOffset(10, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docx() throws Exception {
        String wordNamespace = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
        String part = "application/vnd.openxmlformats-officedocument.wordprocessingml";
        String relationship = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml" ContentType="%1$s.document.main+xml"/>
                    <Override PartName="/word/styles.xml" ContentType="%1$s.styles+xml"/>
                    </Types>""".formatted(part));
            addZipEntry(zip, "_rels/.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="%s/officeDocument" Target="word/document.xml"/>
                    </Relationships>""".formatted(relationship));
            addZipEntry(zip, "word/_rels/document.xml.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="%s/styles" Target="styles.xml"/>
                    </Relationships>""".formatted(relationship));
            addZipEntry(zip, "word/styles.xml", """
                    <w:styles %s><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style></w:styles>
                    """.formatted(wordNamespace));
            addZipEntry(zip, "word/document.xml", """
                    <w:document %s><w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Payment Terms</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Pay within 30 days.</w:t></w:r></w:p>
                    </w:body></w:document>""".formatted(wordNamespace));
        }
        return out.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
