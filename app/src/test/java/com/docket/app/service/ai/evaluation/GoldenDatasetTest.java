package com.docket.app.service.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenDatasetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void qaPair_hasExpectedCitations_returnsTrueWhenPresent() {
        GoldenDataset.QAPair pair = GoldenDataset.QAPair.builder()
                .expectedCitations(List.of(GoldenDataset.ExpectedCitation.builder().build()))
                .build();

        assertTrue(pair.hasExpectedCitations());
    }

    @Test
    void qaPair_hasExpectedCitations_returnsFalseWhenEmpty() {
        GoldenDataset.QAPair pair = GoldenDataset.QAPair.builder()
                .expectedCitations(List.of())
                .build();

        assertFalse(pair.hasExpectedCitations());
    }

    @Test
    void qaPair_hasExpectedCitations_returnsFalseWhenNull() {
        GoldenDataset.QAPair pair = GoldenDataset.QAPair.builder()
                .expectedCitations(null)
                .build();

        assertFalse(pair.hasExpectedCitations());
    }

    @Test
    void dataset_size_returnsCorrectCount() {
        GoldenDataset.Dataset dataset = GoldenDataset.Dataset.builder()
                .qaPairs(List.of(
                        GoldenDataset.QAPair.builder().id("1").build(),
                        GoldenDataset.QAPair.builder().id("2").build(),
                        GoldenDataset.QAPair.builder().id("3").build()
                ))
                .build();

        assertEquals(3, dataset.size());
    }

    @Test
    void dataset_size_returnsZeroWhenNull() {
        GoldenDataset.Dataset dataset = GoldenDataset.Dataset.builder()
                .qaPairs(null)
                .build();

        assertEquals(0, dataset.size());
    }

    @Test
    void evaluationResult_getPassRate_calculatesCorrectly() {
        GoldenDataset.EvaluationResult result = GoldenDataset.EvaluationResult.builder()
                .totalQuestions(10)
                .successfulQueries(8)
                .failedQueries(2)
                .build();

        assertEquals(0.8, result.getPassRate());
    }

    @Test
    void evaluationResult_getPassRate_zeroQuestions_returnsZero() {
        GoldenDataset.EvaluationResult result = GoldenDataset.EvaluationResult.builder()
                .totalQuestions(0)
                .successfulQueries(0)
                .failedQueries(0)
                .build();

        assertEquals(0.0, result.getPassRate());
    }

    @Test
    void serializeAndDeserialize_roundTrip_preservesData() throws IOException {
        GoldenDataset.Dataset original = GoldenDataset.Dataset.builder()
                .name("test-dataset")
                .description("Test description")
                .tenantArchetype("legal")
                .version(2)
                .qaPairs(List.of(
                        GoldenDataset.QAPair.builder()
                                .id("q1")
                                .question("What is X?")
                                .expectedAnswer("X is Y.")
                                .expectedCitations(List.of(
                                        GoldenDataset.ExpectedCitation.builder()
                                                .documentName("doc.pdf")
                                                .documentId("doc")
                                                .chunkIndex(0)
                                                .expectedTextSnippet("X")
                                                .minimumRelevanceScore(0.8)
                                                .build()
                                ))
                                .metadata(Map.of("difficulty", "easy", "category", "test"))
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(original);
        GoldenDataset.Dataset deserialized = objectMapper.readValue(json, GoldenDataset.Dataset.class);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getTenantArchetype(), deserialized.getTenantArchetype());
        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.size(), deserialized.size());
        assertEquals(original.getQaPairs().get(0).getQuestion(), deserialized.getQaPairs().get(0).getQuestion());
        assertEquals(original.getQaPairs().get(0).getExpectedAnswer(), deserialized.getQaPairs().get(0).getExpectedAnswer());
        assertEquals(original.getQaPairs().get(0).getExpectedCitations().size(), deserialized.getQaPairs().get(0).getExpectedCitations().size());
        assertEquals("easy", deserialized.getQaPairs().get(0).getMetadata().get("difficulty"));
    }

    @Test
    void expectedCitation_builder_createsCompleteObject() {
        GoldenDataset.ExpectedCitation citation = GoldenDataset.ExpectedCitation.builder()
                .documentName("test.pdf")
                .documentId("test-doc")
                .chunkIndex(5)
                .expectedTextSnippet("important text")
                .minimumRelevanceScore(0.75)
                .build();

        assertEquals("test.pdf", citation.getDocumentName());
        assertEquals("test-doc", citation.getDocumentId());
        assertEquals(5, citation.getChunkIndex());
        assertEquals("important text", citation.getExpectedTextSnippet());
        assertEquals(0.75, citation.getMinimumRelevanceScore());
    }

    @Test
    void questionResult_builder_createsCompleteObject() {
        GoldenDataset.QuestionResult result = GoldenDataset.QuestionResult.builder()
                .questionId("q1")
                .question("What is X?")
                .actualAnswer("X is Y.")
                .expectedAnswer("X is Y.")
                .retrievedCitations(List.of())
                .citationPrecision(0.9)
                .citationRecall(0.8)
                .answerCorrectness(0.95)
                .latencyMs(150)
                .passed(true)
                .failureReason(null)
                .build();

        assertEquals("q1", result.getQuestionId());
        assertEquals(0.9, result.getCitationPrecision());
        assertEquals(0.8, result.getCitationRecall());
        assertEquals(0.95, result.getAnswerCorrectness());
        assertEquals(150, result.getLatencyMs());
        assertTrue(result.isPassed());
    }
}