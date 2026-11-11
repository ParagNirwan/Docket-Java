package com.docket.app.service.ai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Profile("gcp")
public class GeminiClient {

    private final VertexAI vertexAI;

    public String generate(String modelName, String prompt, GenerationConfig config) throws IOException {
        GenerativeModel model = new GenerativeModel.Builder()
                .setModelName(modelName)
                .setVertexAi(vertexAI)
                .setGenerationConfig(config)
                .build();

        GenerateContentResponse response = model.generateContent(prompt);
        return ResponseHandler.getText(response);
    }

    // Hands over each piece of the answer as the model writes it
    public void generateStream(String modelName, String prompt, GenerationConfig config, Consumer<String> onText)
            throws IOException {
        GenerativeModel model = new GenerativeModel.Builder()
                .setModelName(modelName)
                .setVertexAi(vertexAI)
                .setGenerationConfig(config)
                .build();

        for (GenerateContentResponse response : model.generateContentStream(prompt)) {
            // The last piece often has no text, only the reason the model stopped
            if (response.getCandidatesCount() == 0) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (Part part : response.getCandidates(0).getContent().getPartsList()) {
                if (part.hasText()) {
                    text.append(part.getText());
                }
            }
            if (!text.isEmpty()) {
                onText.accept(text.toString());
            }
        }
    }
}
