package com.docket.app.service.ai.evaluation;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.service.ai.AiQueryService;
import com.docket.app.service.ai.AiSearchScope;
import com.docket.app.service.ai.ConversationTurn;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs AI search quality evaluation against a golden dataset.
 * Measures citation precision/recall, answer correctness (via LLM-as-judge), and latency.
 */
@Component
public class AiSearchEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AiSearchEvaluator.class);

    private final AiQueryService aiQueryService;
    private final ObjectMapper objectMapper;
    private final DocketAiProperties aiProperties;

    @Autowired
    public AiSearchEvaluator(AiQueryService aiQueryService, ObjectMapper objectMapper, DocketAiProperties aiProperties) {
        this.aiQueryService = aiQueryService;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    /**
     * Evaluates the AI search system against a golden dataset.
     */
    public GoldenDataset.EvaluationResult evaluate(
            String authenticationPrincipal,
            UUID tenantId,
            GoldenDataset.Dataset dataset,
            EvaluationConfig config) {

        log.info("Starting AI search evaluation on dataset: {} ({} questions)", dataset.getName(), dataset.size());

        List<GoldenDataset.QuestionResult> questionResults = new ArrayList<>();
        int successful = 0;
        int failed = 0;
        List<Long> latencies = new ArrayList<>();

        for (GoldenDataset.QAPair qaPair : dataset.getQaPairs()) {
            Instant start = Instant.now();
            GoldenDataset.QuestionResult result = evaluateQuestion(
                    authenticationPrincipal,
                    tenantId,
                    qaPair,
                    config);
            Instant end = Instant.now();

            long latencyMs = Duration.between(start, end).toMillis();
            result.setLatencyMs(latencyMs);
            latencies.add(latencyMs);

            if (result.isPassed()) {
                successful++;
            } else {
                failed++;
            }
            questionResults.add(result);
        }

        // Calculate aggregate metrics
        double citationPrecision = questionResults.stream()
                .mapToDouble(GoldenDataset.QuestionResult::getCitationPrecision)
                .average().orElse(0.0);
        double citationRecall = questionResults.stream()
                .mapToDouble(GoldenDataset.QuestionResult::getCitationRecall)
                .average().orElse(0.0);
        double answerCorrectness = questionResults.stream()
                .mapToDouble(GoldenDataset.QuestionResult::getAnswerCorrectness)
                .average().orElse(0.0);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double p95Latency = calculatePercentile(latencies, 95);

        GoldenDataset.EvaluationResult evaluationResult = GoldenDataset.EvaluationResult.builder()
                .datasetName(dataset.getName())
                .totalQuestions(dataset.size())
                .successfulQueries(successful)
                .failedQueries(failed)
                .citationPrecision(citationPrecision)
                .citationRecall(citationRecall)
                .answerCorrectness(answerCorrectness)
                .averageLatencyMs(avgLatency)
                .p95LatencyMs(p95Latency)
                .questionResults(questionResults)
                .build();

        // Record metrics for Prometheus
        Metrics.gauge("docket.ai.evaluation.citation_precision", evaluationResult, GoldenDataset.EvaluationResult::getCitationPrecision);
        Metrics.gauge("docket.ai.evaluation.citation_recall", evaluationResult, GoldenDataset.EvaluationResult::getCitationRecall);
        Metrics.gauge("docket.ai.evaluation.answer_correctness", evaluationResult, GoldenDataset.EvaluationResult::getAnswerCorrectness);
        Metrics.gauge("docket.ai.evaluation.avg_latency_ms", evaluationResult, GoldenDataset.EvaluationResult::getAverageLatencyMs);
        Metrics.gauge("docket.ai.evaluation.pass_rate", evaluationResult, GoldenDataset.EvaluationResult::getPassRate);

        log.info("Evaluation complete: {}/{} passed, precision={:.2f}, recall={:.2f}, correctness={:.2f}, avgLatency={:.0f}ms",
                successful, dataset.size(), citationPrecision, citationRecall, answerCorrectness, avgLatency);

        return evaluationResult;
    }

    private GoldenDataset.QuestionResult evaluateQuestion(
            String authenticationPrincipal,
            UUID tenantId,
            GoldenDataset.QAPair qaPair,
            EvaluationConfig config) {

        String question = qaPair.getQuestion();
        String expectedAnswer = qaPair.getExpectedAnswer();
        List<GoldenDataset.ExpectedCitation> expectedCitations = qaPair.getExpectedCitations();

        try {
            // Execute the AI query
            AiQueryResponseDTO response = aiQueryService.query(
                    createAuthentication(authenticationPrincipal),
                    tenantId,
                    question,
                    null, // documentId - null for workspace search
                    question,
                    List.of(), // no conversation history for evaluation
                    com.docket.app.enums.AiQueryMode.ASK,
                    AiSearchScope.NONE);

            String actualAnswer = response.getAnswer();
            List<AiSourceChunkDTO> sources = response.getSources();

            // Convert to evaluation citations
            List<GoldenDataset.RetrievedCitation> retrievedCitations = sources.stream()
                    .map(s -> GoldenDataset.RetrievedCitation.builder()
                            .documentName(s.getDocumentName())
                            .documentId(s.getDocumentId().toString())
                            .chunkIndex(s.getChunkIndex())
                            .chunkText(s.getChunkText())
                            .relevanceScore(s.getRelevanceScore() != null ? s.getRelevanceScore() : 0.0)
                            .matchesExpected(false)
                            .build())
                    .collect(Collectors.toList());

            // Match retrieved citations to expected citations
            CitationMatchResult matchResult = matchCitations(expectedCitations, retrievedCitations);

            // Mark matches
            for (int i = 0; i < retrievedCitations.size(); i++) {
                if (matchResult.retrievedMatchesExpected.contains(i)) {
                    retrievedCitations.get(i).setMatchesExpected(true);
                }
            }

            // Calculate citation precision/recall
            double citationPrecision = expectedCitations.isEmpty() ? 1.0 :
                    (double) matchResult.matchedExpected.size() / Math.max(1, retrievedCitations.size());
            double citationRecall = expectedCitations.isEmpty() ? 1.0 :
                    (double) matchResult.matchedExpected.size() / expectedCitations.size();

            // Evaluate answer correctness using LLM-as-judge (simplified heuristic for now)
            double answerCorrectness = evaluateAnswerCorrectness(actualAnswer, expectedAnswer, config);

            boolean passed = citationPrecision >= config.getMinCitationPrecision()
                    && citationRecall >= config.getMinCitationRecall()
                    && answerCorrectness >= config.getMinAnswerCorrectness();

            return GoldenDataset.QuestionResult.builder()
                    .questionId(qaPair.getId())
                    .question(question)
                    .actualAnswer(actualAnswer)
                    .expectedAnswer(expectedAnswer)
                    .retrievedCitations(retrievedCitations)
                    .citationPrecision(citationPrecision)
                    .citationRecall(citationRecall)
                    .answerCorrectness(answerCorrectness)
                    .passed(passed)
                    .failureReason(passed ? null : buildFailureReason(citationPrecision, citationRecall, answerCorrectness, config))
                    .build();

        } catch (Exception e) {
            log.error("Evaluation failed for question: {}", qaPair.getId(), e);
            return GoldenDataset.QuestionResult.builder()
                    .questionId(qaPair.getId())
                    .question(question)
                    .actualAnswer("ERROR: " + e.getMessage())
                    .expectedAnswer(expectedAnswer)
                    .retrievedCitations(List.of())
                    .citationPrecision(0.0)
                    .citationRecall(0.0)
                    .answerCorrectness(0.0)
                    .passed(false)
                    .failureReason("Exception: " + e.getMessage())
                    .build();
        }
    }

    private CitationMatchResult matchCitations(
            List<GoldenDataset.ExpectedCitation> expected,
            List<GoldenDataset.RetrievedCitation> retrieved) {

        Set<Integer> matchedExpected = ConcurrentHashMap.newKeySet();
        Set<Integer> retrievedMatchesExpected = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < expected.size(); i++) {
            GoldenDataset.ExpectedCitation exp = expected.get(i);
            for (int j = 0; j < retrieved.size(); j++) {
                GoldenDataset.RetrievedCitation ret = retrieved.get(j);
                if (citationsMatch(exp, ret)) {
                    matchedExpected.add(i);
                    retrievedMatchesExpected.add(j);
                    break; // One expected citation can match at most one retrieved
                }
            }
        }

        return new CitationMatchResult(matchedExpected, retrievedMatchesExpected);
    }

    private boolean citationsMatch(GoldenDataset.ExpectedCitation expected, GoldenDataset.RetrievedCitation retrieved) {
        // Match by document name (fuzzy) and chunk index proximity
        if (expected.getDocumentName() != null && retrieved.getDocumentName() != null) {
            if (!expected.getDocumentName().equalsIgnoreCase(retrieved.getDocumentName())) {
                return false;
            }
        }
        // Check chunk index proximity (within 1)
        if (Math.abs(expected.getChunkIndex() - retrieved.getChunkIndex()) > 1) {
            return false;
        }
        // Check relevance score meets minimum
        if (retrieved.getRelevanceScore() < expected.getMinimumRelevanceScore()) {
            return false;
        }
        return true;
    }

    private double evaluateAnswerCorrectness(String actual, String expected, EvaluationConfig config) {
        if (config.isUseLlmAsJudge()) {
            // TODO: Implement LLM-as-judge evaluation
            // For now, use a simple heuristic based on keyword overlap
            return heuristicAnswerSimilarity(actual, expected);
        }
        return heuristicAnswerSimilarity(actual, expected);
    }

    private double heuristicAnswerSimilarity(String actual, String expected) {
        if (actual == null || expected == null) return 0.0;
        if (actual.equals(expected)) return 1.0;

        // Simple keyword overlap score
        Set<String> actualWords = Set.of(actual.toLowerCase().split("\\W+"));
        Set<String> expectedWords = Set.of(expected.toLowerCase().split("\\W+"));

        if (expectedWords.isEmpty()) return 1.0;

        Set<String> intersection = new java.util.HashSet<>(actualWords);
        intersection.retainAll(expectedWords);

        return (double) intersection.size() / expectedWords.size();
    }

    private String buildFailureReason(double precision, double recall, double correctness, EvaluationConfig config) {
        StringBuilder sb = new StringBuilder();
        if (precision < config.getMinCitationPrecision()) {
            sb.append("Citation precision ").append(String.format("%.2f", precision)).append(" < ").append(config.getMinCitationPrecision()).append("; ");
        }
        if (recall < config.getMinCitationRecall()) {
            sb.append("Citation recall ").append(String.format("%.2f", recall)).append(" < ").append(config.getMinCitationRecall()).append("; ");
        }
        if (correctness < config.getMinAnswerCorrectness()) {
            sb.append("Answer correctness ").append(String.format("%.2f", correctness)).append(" < ").append(config.getMinAnswerCorrectness());
        }
        return sb.toString();
    }

    private org.springframework.security.core.Authentication createAuthentication(String principal) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, "credentials", List.of());
    }

    private double calculatePercentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0.0;
        List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static class CitationMatchResult {
        final Set<Integer> matchedExpected;
        final Set<Integer> retrievedMatchesExpected;

        CitationMatchResult(Set<Integer> matchedExpected, Set<Integer> retrievedMatchesExpected) {
            this.matchedExpected = matchedExpected;
            this.retrievedMatchesExpected = retrievedMatchesExpected;
        }
    }
}