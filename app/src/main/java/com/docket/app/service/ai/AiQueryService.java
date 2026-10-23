package com.docket.app.service.ai;

import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.repository.DocumentChunkRepository;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class AiQueryService {

    private static final Logger log = LoggerFactory.getLogger(AiQueryService.class);
    private static final int TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final VertexAI vertexAI;

    @org.springframework.beans.factory.annotation.Value("${docket.ai.gemini.model}")
    private String geminiModel;

    public AiQueryResponseDTO query(UUID tenantId, String question, UUID documentId) {
        // Step 1: Embed the user's question
        List<float[]> questionEmbedding = embeddingService.embed(List.of(question));
        String embeddingString = toVectorString(questionEmbedding.get(0));

        // Step 2: Find nearest chunks via pgvector
        List<Object[]> rows;
        if (documentId != null) {
            rows = documentChunkRepository.findNearestByTenantAndDocument(
                    tenantId, documentId, embeddingString, TOP_K);
        } else {
            rows = documentChunkRepository.findNearestByTenant(
                    tenantId, embeddingString, TOP_K);
        }

        if (rows.isEmpty()) {
            return new AiQueryResponseDTO(
                    "No indexed documents found. Please upload and wait for documents to finish processing.",
                    List.of());
        }

        // Step 3: Extract chunk data from raw rows
        List<AiSourceChunkDTO> sources = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            UUID chunkDocId = (UUID) row[2];       // document_id
            Integer chunkIndex = (Integer) row[3];  // chunk_index
            String chunkText = (String) row[4];     // chunk_text
            String docName = (String) row[7];       // document_name (from JOIN)

            sources.add(new AiSourceChunkDTO(chunkDocId, docName, chunkText, chunkIndex));

            contextBuilder.append("[Source ").append(i + 1).append(": ")
                    .append(docName).append("]\n")
                    .append(chunkText).append("\n\n");
        }

        // Step 4: Build prompt and call Gemini
        String answer = callGemini(question, contextBuilder.toString());

        return new AiQueryResponseDTO(answer, sources);
    }

    private String callGemini(String question, String context) {
        try {
            String systemPrompt = """
                    You are a helpful document assistant for the Docket platform.
                    Answer the user's question based ONLY on the provided context from their documents.
                    If the context doesn't contain enough information to answer, say "I don't have enough information in the provided documents to answer this question."
                    When answering, reference which source(s) you used (e.g., "According to Source 1...").
                    Keep answers concise and factual.
                    """;

            String fullPrompt = systemPrompt + "\n\n--- CONTEXT ---\n" + context
                    + "\n--- QUESTION ---\n" + question;

            GenerationConfig generationConfig = GenerationConfig.newBuilder()
                    .setTemperature(0.3f)
                    .setMaxOutputTokens(1024)
                    .build();

            GenerativeModel model = new GenerativeModel.Builder()
                    .setModelName(geminiModel)
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(generationConfig)
                    .build();

            GenerateContentResponse response = model.generateContent(fullPrompt);

            return ResponseHandler.getText(response);

        } catch (Exception e) {
            log.error("Gemini call failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI service is temporarily unavailable. Please try again in a moment.");
        }
    }

   private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(vector[i]);
    }
    return sb.append("]").toString();
}

}
