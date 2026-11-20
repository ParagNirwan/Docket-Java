package com.docket.app.service.ai.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Golden dataset for AI search quality evaluation.
 * Contains question-answer pairs with expected citations for benchmarking.
 */
public final class GoldenDataset {

    private GoldenDataset() {}

    /**
     * Loads a golden dataset from a JSON resource file.
     */
    public static Dataset loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = GoldenDataset.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new ObjectMapper().readValue(is, Dataset.class);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dataset {
        private String name;
        private String description;
        private String tenantArchetype; // legal, finance, ops
        private int version;
        private List<QAPair> qaPairs;

        public int size() {
            return qaPairs != null ? qaPairs.size() : 0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QAPair {
        private String id;
        private String question;
        private String expectedAnswer;
        private List<ExpectedCitation> expectedCitations;
        private Map<String, Object> metadata; // difficulty, category, etc.

        public boolean hasExpectedCitations() {
            return expectedCitations != null && !expectedCitations.isEmpty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpectedCitation {
        private String documentName;
        private String documentId; // can be UUID string or name-based lookup
        private int chunkIndex;
        private String expectedTextSnippet; // text that should appear in the answer
        private double minimumRelevanceScore; // 0.0 to 1.0
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationResult {
        private String datasetName;
        private int totalQuestions;
        private int successfulQueries;
        private int failedQueries;
        private double citationPrecision;
        private double citationRecall;
        private double answerCorrectness; // LLM-as-judge score
        private double averageLatencyMs;
        private double p95LatencyMs;
        private List<QuestionResult> questionResults;

        public double getPassRate() {
            return totalQuestions > 0 ? (double) successfulQueries / totalQuestions : 0.0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResult {
        private String questionId;
        private String question;
        private String actualAnswer;
        private String expectedAnswer;
        private List<RetrievedCitation> retrievedCitations;
        private double citationPrecision;
        private double citationRecall;
        private double answerCorrectness;
        private long latencyMs;
        private boolean passed;
        private String failureReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedCitation {
        private String documentName;
        private String documentId;
        private int chunkIndex;
        private String chunkText;
        private double relevanceScore;
        private boolean matchesExpected;
    }
}