package com.docket.app.service.ai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;

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
}
