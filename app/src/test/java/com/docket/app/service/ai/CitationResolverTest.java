package com.docket.app.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitationResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CitationResolver.ResolvedAnswer resolve(String output, int sourceCount) {
        return CitationResolver.resolve(output, sourceCount, objectMapper);
    }

    @Test
    void resolve_renumbersMarkersInOrderOfAppearance() {
        var result = resolve("""
                {"answer": "Rent is 500 [4]. It rises yearly [2]. Again, 500 [4].",
                 "citations": [{"sourceId": 2}, {"sourceId": 4}]}
                """, 5);

        assertEquals("Rent is 500 [1]. It rises yearly [2]. Again, 500 [1].", result.answer());
        assertEquals(List.of(3, 1), result.cited());
    }

    @Test
    void resolve_dropsMadeUpSources() {
        var result = resolve("""
                {"answer": "Rent is 500 [1] and more [9].", "citations": [{"sourceId": 1}, {"sourceId": 9}, {"sourceId": 0}]}
                """, 2);

        assertEquals("Rent is 500 [1] and more .", result.answer());
        assertEquals(List.of(0), result.cited());
    }

    @Test
    void resolve_addsMarkerForSourceDeclaredButNotInText() {
        var result = resolve("""
                {"answer": "Rent is 500.", "citations": [{"sourceId": 2}]}
                """, 2);

        assertEquals("Rent is 500. [1]", result.answer());
        assertEquals(List.of(1), result.cited());
    }

    @Test
    void resolve_splitsCombinedMarkers() {
        var result = resolve("""
                {"answer": "Rent is 500 [2, 1].", "citations": []}
                """, 2);

        assertEquals("Rent is 500 [1][2].", result.answer());
        assertEquals(List.of(1, 0), result.cited());
    }

    @Test
    void resolve_noCitations_hasNoSources() {
        var result = resolve("""
                {"answer": "The documents don't say.", "citations": []}
                """, 3);

        assertEquals("The documents don't say.", result.answer());
        assertEquals(List.of(), result.cited());
    }

    @Test
    void resolve_plainTextFromModel_stillFindsMarkers() {
        var result = resolve("Rent is 500 [2].", 2);

        assertEquals("Rent is 500 [1].", result.answer());
        assertEquals(List.of(1), result.cited());
    }

    @Test
    void resolve_textWithDollarSign_isKeptAsWritten() {
        assertEquals("Costs $5 [1].", resolve("""
                {"answer": "Costs $5 [1].", "citations": []}
                """, 1).answer());
    }

    @Test
    void resolve_unfinishedJson_throws() {
        assertThrows(IllegalStateException.class, () -> resolve("{\"answer\": \"Rent is", 2));
    }

    @Test
    void resolve_jsonWithoutAnswer_throws() {
        assertThrows(IllegalStateException.class, () -> resolve("{\"citations\": []}", 2));
    }
}
