package com.docket.app.service.ai.evaluation;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Configuration for AI search evaluation runs.
 */
@Data
@Builder
public class EvaluationConfig {

    /**
     * Minimum citation precision required to pass (0.0 to 1.0).
     */
    @Builder.Default
    private double minCitationPrecision = 0.7;

    /**
     * Minimum citation recall required to pass (0.0 to 1.0).
     */
    @Builder.Default
    private double minCitationRecall = 0.7;

    /**
     * Minimum answer correctness required to pass (0.0 to 1.0).
     */
    @Builder.Default
    private double minAnswerCorrectness = 0.6;

    /**
     * Whether to use LLM-as-judge for answer correctness evaluation.
     * If false, uses heuristic keyword overlap.
     */
    @Builder.Default
    private boolean useLlmAsJudge = false;

    /**
     * Maximum number of questions to evaluate (0 = all).
     */
    @Builder.Default
    private int maxQuestions = 0;

    /**
     * Filter to only evaluate questions with specific tags in metadata.
     */
    private Set<String> questionTags;

    /**
     * Whether to include questions with no expected citations.
     */
    @Builder.Default
    private boolean includeUncitedQuestions = true;
}