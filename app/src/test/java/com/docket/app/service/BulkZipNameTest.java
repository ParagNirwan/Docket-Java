package com.docket.app.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkZipNameTest {

    @Test
    void stripsPathsSoEntriesCantEscapeTheExtractFolder() {
        Set<String> used = new HashSet<>();
        assertEquals("passwd", BulkDocumentService.uniqueName("../../etc/passwd", used));
        assertEquals("evil.exe", BulkDocumentService.uniqueName("C:\\Windows\\evil.exe", used));
        assertEquals("document", BulkDocumentService.uniqueName("..", used));
    }

    @Test
    void deDuplicatesNamesCaseInsensitively() {
        Set<String> used = new HashSet<>();
        assertEquals("Report.pdf", BulkDocumentService.uniqueName("Report.pdf", used));
        assertEquals("report (2).pdf", BulkDocumentService.uniqueName("report.pdf", used));
        assertEquals("Report (3).pdf", BulkDocumentService.uniqueName("Report.pdf", used));
    }
}
