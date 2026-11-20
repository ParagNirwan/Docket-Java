package com.docket.app.service.ai.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitationCoverageTest {

    @Test
    void countsSubstantiveSentencesThatCiteASource() {
        String answer = "The lease runs until December 2026 [1]. Rent increases by 3% every year [2]. "
                + "Either party may terminate with sixty days notice.";

        assertEquals(2.0 / 3.0, AiSearchEvaluator.citationCoverage(answer), 1e-9);
    }

    @Test
    void ignoresShortFillerSentences() {
        assertEquals(1.0, AiSearchEvaluator.citationCoverage("Yes. The notice period is sixty days [1]."), 1e-9);
    }

    @Test
    void emptyOrTrivialAnswersAreFullyCovered() {
        assertEquals(1.0, AiSearchEvaluator.citationCoverage(""), 1e-9);
        assertEquals(1.0, AiSearchEvaluator.citationCoverage("No."), 1e-9);
    }
}
