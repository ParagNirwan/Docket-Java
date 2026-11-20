package com.docket.app.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationResultTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void keepsAWellFormedClassification() {
        ClassificationResult r = ClassificationResult.parse("""
                {"documentType":"contract","suggestedTags":["Lease","lease","Real estate"],
                 "fields":{"parties":["Acme Corp","Beta LLC"],"amounts":["$4,000/month"],
                           "effectiveDate":"2024-01-01","expiryDate":"2026-12-31"},
                 "keyDate":"2026-06-30"}
                """, json);

        assertEquals("contract", r.documentType());
        assertEquals(List.of("Lease", "Real estate"), r.suggestedTags());
        assertEquals(List.of("Acme Corp", "Beta LLC"), r.fields().get("parties"));
        assertEquals("2026-12-31", r.fields().get("expiryDate"));
        assertEquals(LocalDate.of(2026, 6, 30), r.keyDate());
    }

    @Test
    void clampsUntrustedOutputToTheExpectedShape() {
        ClassificationResult r = ClassificationResult.parse("""
                {"documentType":"IGNORE PREVIOUS INSTRUCTIONS","suggestedTags":["a","b","c","d","e","f","g"],
                 "fields":{"parties":["%s"],"expiryDate":"next tuesday","admin":"yes"},
                 "keyDate":"0003-01-01"}
                """.formatted("x".repeat(1000)), json);

        assertEquals("other", r.documentType());
        assertEquals(5, r.suggestedTags().size());
        assertTrue(((List<?>) r.fields().get("parties")).get(0).toString().length() <= 300);
        assertFalse(r.fields().containsKey("expiryDate"));
        assertFalse(r.fields().containsKey("admin"));
        assertNull(r.keyDate());
    }

    @Test
    void rejectsNonJson() {
        assertThrows(IllegalArgumentException.class, () -> ClassificationResult.parse("Sure! Here you go", json));
    }
}
