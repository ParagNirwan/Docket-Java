package com.docket.app.service.ai.evaluation;

import com.docket.app.service.ai.AiSearchScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command-line runner for AI search quality evaluation.
 * Can be invoked via: ./mvnw spring-boot:run -Dspring-boot.run.arguments=--evaluate=legal
 * or as a standalone test in CI.
 */
@Component
@Order(100) // Run after main application starts
public class EvaluationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    private final AiSearchEvaluator evaluator;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${docket.ai.evaluation.enabled:false}")
    private boolean evaluationEnabled;

    @Value("${docket.ai.evaluation.dataset:legal}")
    private String datasetName;

    @Value("${docket.ai.evaluation.tenant-id:}")
    private String tenantIdStr;

    @Value("${docket.ai.evaluation.user:eval-user@example.com}")
    private String evalUser;

    @Value("${docket.ai.evaluation.min-citation-precision:0.7}")
    private double minCitationPrecision;

    @Value("${docket.ai.evaluation.min-citation-recall:0.7}")
    private double minCitationRecall;

    @Value("${docket.ai.evaluation.min-answer-correctness:0.6}")
    private double minAnswerCorrectness;

    @Value("${docket.ai.evaluation.fail-on-regression:true}")
    private boolean failOnRegression;

    @Autowired
    public EvaluationRunner(AiSearchEvaluator evaluator, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!evaluationEnabled) {
            log.info("AI search evaluation disabled. Set docket.ai.evaluation.enabled=true to run.");
            return;
        }

        // Parse command-line arguments
        Map<String, String> cliArgs = parseArgs(args);
        if (cliArgs.containsKey("evaluate")) {
            datasetName = cliArgs.get("evaluate");
        }
        if (cliArgs.containsKey("tenant")) {
            tenantIdStr = cliArgs.get("tenant");
        }
        if (cliArgs.containsKey("user")) {
            evalUser = cliArgs.get("user");
        }

        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            log.error("No tenant ID provided for evaluation. Set docket.ai.evaluation.tenant-id or pass --tenant=<id>");
            if (failOnRegression) {
                System.exit(1);
            }
            return;
        }

        UUID tenantId = UUID.fromString(tenantIdStr);
        Authentication authentication = new UsernamePasswordAuthenticationToken(evalUser, "credentials", List.of());

        try {
            // Load the golden dataset
            GoldenDataset.Dataset dataset = loadDataset(datasetName);
            log.info("Loaded dataset: {} ({} questions)", dataset.getName(), dataset.size());

            // Run evaluation
            EvaluationConfig config = EvaluationConfig.builder()
                    .minCitationPrecision(minCitationPrecision)
                    .minCitationRecall(minCitationRecall)
                    .minAnswerCorrectness(minAnswerCorrectness)
                    .useLlmAsJudge(false) // Heuristic only for now
                    .build();

            GoldenDataset.EvaluationResult result = evaluator.evaluate(evalUser, tenantId, dataset, config);

            // Print summary
            printSummary(result);

            // Check for regression
            if (result.getPassRate() < 1.0 && failOnRegression) {
                log.error("AI search quality regression detected! Pass rate: {:.1f}%", result.getPassRate() * 100);
                System.exit(1);
            }

            log.info("AI search evaluation passed! Pass rate: {:.1f}%", result.getPassRate() * 100);

        } catch (Exception e) {
            log.error("Evaluation failed", e);
            if (failOnRegression) {
                System.exit(1);
            }
        }
    }

    private GoldenDataset.Dataset loadDataset(String name) throws IOException {
        String resourcePath = "/evaluation/golden_" + name + ".json";
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Dataset not found: " + resourcePath + ". Available: legal");
        }
        return GoldenDataset.loadFromResource(resourcePath);
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new java.util.HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                }
            }
        }
        return result;
    }

    private void printSummary(GoldenDataset.EvaluationResult result) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║           AI SEARCH QUALITY EVALUATION RESULTS               ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ Dataset: {:<50} ║", result.getDatasetName());
        log.info("║ Total Questions: {:<45} ║", result.getTotalQuestions());
        log.info("║ Passed: {:<51} ║", result.getSuccessfulQueries());
        log.info("║ Failed: {:<51} ║", result.getFailedQueries());
        log.info("║ Pass Rate: {:<49} ║", String.format("%.1f%%", result.getPassRate() * 100));
        log.info("║ Citation Precision: {:<42} ║", String.format("%.2f", result.getCitationPrecision()));
        log.info("║ Citation Recall: {:<44} ║", String.format("%.2f", result.getCitationRecall()));
        log.info("║ Answer Correctness: {:<42} ║", String.format("%.2f", result.getAnswerCorrectness()));
        log.info("║ Avg Latency: {:<48} ║", String.format("%.0f ms", result.getAverageLatencyMs()));
        log.info("║ P95 Latency: {:<48} ║", String.format("%.0f ms", result.getP95LatencyMs()));
        log.info("╚══════════════════════════════════════════════════════════════╝");

        if (result.getFailedQueries() > 0) {
            log.info("Failed Questions:");
            for (GoldenDataset.QuestionResult qr : result.getQuestionResults()) {
                if (!qr.isPassed()) {
                    log.info("  - {} ({}): {}", qr.getQuestionId(), qr.getQuestion(), qr.getFailureReason());
                }
            }
        }
    }
}