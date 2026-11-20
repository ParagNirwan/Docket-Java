package com.docket.app.service.ai.evaluation;

import com.docket.app.config.DocketAiProperties;
import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.service.ai.AiQueryService;
import com.docket.app.service.ai.AiSearchScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class AiSearchEvaluatorTest {

    @Mock
    private AiQueryService aiQueryService;

    @Mock
    private DocketAiProperties aiProperties;

    @InjectMocks
    private AiSearchEvaluator evaluator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID tenantId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken("test@example.com", "credentials", List.of());
    }

    @Test
    void evaluate_allQuestionsPass_returnsPassingResult() throws Exception {
        // Create a simple golden dataset
        GoldenDataset.Dataset dataset = GoldenDataset.Dataset.builder()
                .name("test-dataset")
                .tenantArchetype("legal")
                .version(1)
                .qaPairs(List.of(
                        createQAPair("q1", "What is the termination clause?", "Termination requires 60 days notice.", List.of(
                                createExpectedCitation("lease.pdf", "lease", 3, "termination", 0.7)
                        )),
                        createQAPair("q2", "What is the payment term?", "Net 30 days.", List.of(
                                createExpectedCitation("invoice.pdf", "invoice", 1, "30 days", 0.8)
                        ))
                ))
                .build();

        // Mock AI query service to return matching results
        when(aiQueryService.query(eq(authentication), eq(tenantId), anyString(), eq((UUID)null), anyString(), eq(List.of()), any(), eq(AiSearchScope.NONE)))
                .thenAnswer(invocation -> {
                    String question = invocation.getArgument(2); // question is 3rd argument
                    if (question.contains("termination")) {
                        return new AiQueryResponseDTO(
                                "Termination requires 60 days notice. [1]",
                                List.of(createSourceChunk("lease.pdf", "lease", 3, "Termination requires 60 days notice", 0.85))
                        );
                    } else {
                        return new AiQueryResponseDTO(
                                "Net 30 days. [1]",
                                List.of(createSourceChunk("invoice.pdf", "invoice", 1, "Payment due in 30 days", 0.9))
                        );
                    }
                });

        EvaluationConfig config = EvaluationConfig.builder()
                .minCitationPrecision(0.5)
                .minCitationRecall(0.5)
                .minAnswerCorrectness(0.5)
                .build();

        GoldenDataset.EvaluationResult result = evaluator.evaluate("test@example.com", tenantId, dataset, config);

        assertEquals(2, result.getTotalQuestions());
        assertEquals(2, result.getSuccessfulQueries());
        assertEquals(0, result.getFailedQueries());
        assertTrue(result.getCitationPrecision() > 0.5);
        assertTrue(result.getCitationRecall() > 0.5);
        assertTrue(result.getAnswerCorrectness() > 0.5);
        assertTrue(result.getPassRate() == 1.0);
    }

    @Test
    void evaluate_missingCitations_returnsLowPrecision() throws Exception {
        GoldenDataset.Dataset dataset = GoldenDataset.Dataset.builder()
                .name("test-dataset")
                .tenantArchetype("legal")
                .version(1)
                .qaPairs(List.of(
                        createQAPair("q1", "What is the termination clause?", "Termination requires 60 days notice.", List.of(
                                createExpectedCitation("lease.pdf", "lease", 3, "termination", 0.7)
                        ))
                ))
                .build();

        // Return answer with NO sources
        when(aiQueryService.query(eq(authentication), eq(tenantId), anyString(), eq(null), anyString(), eq(List.of()), any(), eq(AiSearchScope.NONE)))
                .thenReturn(new AiQueryResponseDTO(
                        "Termination requires 60 days notice.",
                        List.of() // No citations!
                ));

        EvaluationConfig config = EvaluationConfig.builder()
                .minCitationPrecision(0.7)
                .minCitationRecall(0.7)
                .minAnswerCorrectness(0.5)
                .build();

        GoldenDataset.EvaluationResult result = evaluator.evaluate("test@example.com", tenantId, dataset, config);

        assertEquals(1, result.getTotalQuestions());
        assertEquals(0, result.getSuccessfulQueries());
        assertEquals(1, result.getFailedQueries());
        assertEquals(0.0, result.getCitationPrecision());
        assertEquals(0.0, result.getCitationRecall());
        assertFalse(result.getQuestionResults().get(0).isPassed());
    }

    @Test
    void evaluate_wrongDocumentReturned_returnsLowPrecision() throws Exception {
        GoldenDataset.Dataset dataset = GoldenDataset.Dataset.builder()
                .name("test-dataset")
                .tenantArchetype("legal")
                .version(1)
                .qaPairs(List.of(
                        createQAPair("q1", "What is the termination clause?", "Termination requires 60 days notice.", List.of(
                                createExpectedCitation("lease.pdf", "lease", 3, "termination", 0.7)
                        ))
                ))
                .build();

        // Return answer citing a DIFFERENT document
        when(aiQueryService.query(eq(authentication), eq(tenantId), anyString(), eq(null), anyString(), eq(List.of()), any(), eq(AiSearchScope.NONE)))
                .thenReturn(new AiQueryResponseDTO(
                        "Termination requires 60 days notice. [1]",
                        List.of(createSourceChunk("other.pdf", "other", 0, "Some other text", 0.5))
                ));

        EvaluationConfig config = EvaluationConfig.builder()
                .minCitationPrecision(0.7)
                .minCitationRecall(0.7)
                .minAnswerCorrectness(0.5)
                .build();

        GoldenDataset.EvaluationResult result = evaluator.evaluate("test@example.com", tenantId, dataset, config);

        assertEquals(1, result.getTotalQuestions());
        assertEquals(0, result.getSuccessfulQueries());
        assertEquals(1, result.getFailedQueries());
        assertEquals(0.0, result.getCitationPrecision()); // No matching document
        assertEquals(0.0, result.getCitationRecall());
    }

    @Test
    void loadGoldenDataset_fromResource_parsesCorrectly() throws Exception {
        GoldenDataset.Dataset dataset = GoldenDataset.loadFromResource("/evaluation/golden_legal.json");

        assertEquals("legal-archetype-v1", dataset.getName());
        assertEquals("legal", dataset.getTenantArchetype());
        assertEquals(10, dataset.size());
        assertEquals("What is the termination clause in the lease agreement?", dataset.getQaPairs().get(0).getQuestion());
        assertEquals(1, dataset.getQaPairs().get(0).getExpectedCitations().size());
    }

    private GoldenDataset.QAPair createQAPair(String id, String question, String expectedAnswer, List<GoldenDataset.ExpectedCitation> citations) {
        return GoldenDataset.QAPair.builder()
                .id(id)
                .question(question)
                .expectedAnswer(expectedAnswer)
                .expectedCitations(citations)
                .build();
    }

    private GoldenDataset.ExpectedCitation createExpectedCitation(String docName, String docId, int chunkIndex, String snippet, double minScore) {
        return GoldenDataset.ExpectedCitation.builder()
                .documentName(docName)
                .documentId(docId)
                .chunkIndex(chunkIndex)
                .expectedTextSnippet(snippet)
                .minimumRelevanceScore(minScore)
                .build();
    }

    private AiSourceChunkDTO createSourceChunk(String docName, String docId, int chunkIndex, String text, double relevance) {
        return new AiSourceChunkDTO(
                UUID.randomUUID(),
                docName,
                text,
                chunkIndex,
                relevance,
                1,
                2,
                "Section"
        );
    }
}